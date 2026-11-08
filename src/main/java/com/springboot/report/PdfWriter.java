package com.springboot.report;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** 위에서 아래로 써 내려가는 간단한 A4 PDF 작성 도구 (줄바꿈·표·그림·쪽 넘김). */
public final class PdfWriter implements AutoCloseable {

	static final float MARGIN = 48;
	private static final float[] TEXT = { 0.12f, 0.16f, 0.12f };
	private static final float[] MUTED = { 0.36f, 0.42f, 0.36f };
	private static final float[] ACCENT = { 0.18f, 0.49f, 0.29f };

	private final PDDocument doc = new PDDocument();
	private final PDType0Font font;
	private PDPageContentStream cs;
	private float y;
	private final float width = PDRectangle.A4.getWidth() - MARGIN * 2;

	public PdfWriter(KoreanFont koreanFont) throws IOException {
		this.font = koreanFont.load(doc);
		newPage();
	}

	public float width() {
		return width;
	}

	public void newPage() throws IOException {
		if (cs != null) {
			cs.close();
		}
		PDPage page = new PDPage(PDRectangle.A4);
		doc.addPage(page);
		cs = new PDPageContentStream(doc, page);
		y = PDRectangle.A4.getHeight() - MARGIN;
	}

	private void need(float h) throws IOException {
		if (y - h < MARGIN) {
			newPage();
		}
	}

	/** 글꼴에 없는 글자는 바꾼다. */
	public String safe(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		s.codePoints().forEach(cp -> {
			String ch = new String(Character.toChars(cp));
			try {
				font.encode(ch);
				b.append(ch);
			} catch (IllegalArgumentException | IOException e) {
				b.append(switch (ch) {
					case "㎡" -> "m2";
					case "℃" -> "°C";
					case "·" -> "-";
					default -> "?";
				});
			}
		});
		return b.toString();
	}

	private float textWidth(String s, float size) throws IOException {
		return font.getStringWidth(s) / 1000 * size;
	}

	public void text(String s, float size, float[] rgb, float indent) throws IOException {
		for (String line : wrap(safe(s), size, width - indent)) {
			need(size * 1.45f);
			cs.beginText();
			cs.setFont(font, size);
			cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
			cs.newLineAtOffset(MARGIN + indent, y - size);
			cs.showText(line);
			cs.endText();
			y -= size * 1.45f;
		}
	}

	public void title(String s) throws IOException {
		text(s, 20, ACCENT, 0);
		y -= 4;
	}

	public void heading(String s) throws IOException {
		need(40);
		y -= 10;
		text(s, 13, ACCENT, 0);
		cs.setStrokingColor(0.85f, 0.87f, 0.83f);
		cs.moveTo(MARGIN, y + 2);
		cs.lineTo(MARGIN + width, y + 2);
		cs.stroke();
		y -= 6;
	}

	public void paragraph(String s) throws IOException {
		text(s, 10, TEXT, 0);
	}

	public void muted(String s) throws IOException {
		text(s, 9, MUTED, 0);
	}

	public void bullet(String s) throws IOException {
		text("- " + s, 10, TEXT, 8);
	}

	public void space(float h) {
		y -= h;
	}

	/** 표: 첫 줄은 머리글. widths 는 비율. */
	public void table(List<String[]> rows, float[] widths) throws IOException {
		float total = 0;
		for (float w : widths) {
			total += w;
		}
		float size = 9;
		boolean header = true;
		for (String[] row : rows) {
			// 칸마다 줄바꿈한 뒤 가장 긴 칸 높이만큼
			List<List<String>> cells = new ArrayList<>();
			int lines = 1;
			for (int i = 0; i < row.length; i++) {
				List<String> wrapped = wrap(safe(row[i]), size, width * widths[i] / total - 6);
				cells.add(wrapped);
				lines = Math.max(lines, wrapped.size());
			}
			float h = lines * size * 1.35f + 6;
			need(h);
			if (header) {
				cs.setNonStrokingColor(0.89f, 0.95f, 0.91f);
				cs.addRect(MARGIN, y - h, width, h);
				cs.fill();
			}
			float x = MARGIN;
			for (int i = 0; i < row.length; i++) {
				float ly = y - 3;
				for (String line : cells.get(i)) {
					cs.beginText();
					cs.setFont(font, size);
					cs.setNonStrokingColor(TEXT[0], TEXT[1], TEXT[2]);
					cs.newLineAtOffset(x + 3, ly - size);
					cs.showText(line);
					cs.endText();
					ly -= size * 1.35f;
				}
				x += width * widths[i] / total;
			}
			cs.setStrokingColor(0.85f, 0.87f, 0.83f);
			cs.moveTo(MARGIN, y - h);
			cs.lineTo(MARGIN + width, y - h);
			cs.stroke();
			y -= h;
			header = false;
		}
		y -= 6;
	}

	/** 그림 여러 장을 한 줄에 (캡션 포함). */
	public void images(List<BufferedImage> images, List<String> captions, float eachWidth) throws IOException {
		float maxH = 0;
		for (BufferedImage img : images) {
			maxH = Math.max(maxH, eachWidth * img.getHeight() / img.getWidth());
		}
		need(maxH + 20);
		float x = MARGIN;
		for (int i = 0; i < images.size(); i++) {
			BufferedImage img = images.get(i);
			float h = eachWidth * img.getHeight() / img.getWidth();
			PDImageXObject xo = LosslessFactory.createFromImage(doc, img);
			cs.drawImage(xo, x, y - h, eachWidth, h);
			if (captions != null && i < captions.size()) {
				cs.beginText();
				cs.setFont(font, 9);
				cs.setNonStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
				cs.newLineAtOffset(x, y - maxH - 12);
				cs.showText(safe(captions.get(i)));
				cs.endText();
			}
			x += eachWidth + 10;
		}
		y -= maxH + 20;
	}

	private List<String> wrap(String s, float size, float max) throws IOException {
		List<String> lines = new ArrayList<>();
		for (String para : s.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split("(?<= )")) {
				if (textWidth(line + word, size) <= max) {
					line.append(word);
					continue;
				}
				if (!line.isEmpty()) {
					lines.add(line.toString().stripTrailing());
					line.setLength(0);
				}
				// 한 낱말이 너무 길면 글자 단위로 자른다
				for (int i = 0; i < word.length(); i++) {
					if (textWidth(line.toString() + word.charAt(i), size) > max && !line.isEmpty()) {
						lines.add(line.toString());
						line.setLength(0);
					}
					line.append(word.charAt(i));
				}
			}
			lines.add(line.toString().stripTrailing());
		}
		return lines;
	}

	public byte[] toBytes() throws IOException {
		cs.close();
		cs = null;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			doc.save(out);
			return out.toByteArray();
		}
	}

	@Override
	public void close() throws IOException {
		if (cs != null) {
			cs.close();
		}
		doc.close();
	}
}
