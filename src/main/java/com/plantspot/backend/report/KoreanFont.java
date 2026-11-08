package com.plantspot.backend.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.plantspot.backend.common.UnavailableException;

/**
 * PDF용 한글 글꼴 찾기. app.report.font 에 경로를 주면 그것을, 없으면 운영체제 기본 한글 글꼴을 찾는다.
 * (윈도 맑은 고딕, 맥 Apple SD 산돌고딕 Neo, 리눅스 나눔고딕·Noto CJK)
 */
@Component
public class KoreanFont {

	private static final List<String> CANDIDATES = List.of(
			"C:/Windows/Fonts/malgun.ttf",
			"C:/Windows/Fonts/NanumGothic.ttf",
			"/System/Library/Fonts/AppleSDGothicNeo.ttc",
			"/Library/Fonts/NanumGothic.ttf",
			"/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
			"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
			"/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
			"/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");

	private final String configured;

	public KoreanFont(@Value("${app.report.font:}") String configured) {
		this.configured = configured;
	}

	public Path path() {
		if (!configured.isBlank()) {
			Path p = Path.of(configured);
			if (Files.isRegularFile(p)) {
				return p;
			}
			throw new UnavailableException("app.report.font 글꼴 파일이 없습니다: " + configured);
		}
		return CANDIDATES.stream().map(Path::of).filter(Files::isRegularFile).findFirst()
				.orElseThrow(() -> new UnavailableException(
						"PDF에 쓸 한글 글꼴을 찾지 못했습니다. application-local.properties 의 app.report.font 에 .ttf 경로를 지정하세요."));
	}

	/** 문서에 글꼴을 싣는다(쓰인 글자만 포함). .ttc 는 첫 번째 글꼴. */
	public PDType0Font load(PDDocument doc) throws IOException {
		Path p = path();
		if (p.toString().toLowerCase().endsWith(".ttc")) {
			TrueTypeCollection ttc = new TrueTypeCollection(p.toFile());
			TrueTypeFont[] first = new TrueTypeFont[1];
			ttc.processAllFonts(f -> {
				if (first[0] == null) {
					first[0] = f;
				}
			});
			return PDType0Font.load(doc, first[0], true);
		}
		return PDType0Font.load(doc, p.toFile());
	}
}
