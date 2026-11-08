package com.plantspot.backend.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

import javax.imageio.ImageIO;

import com.plantspot.backend.house.Point;
import com.plantspot.backend.sunlight.LightLevel;
import com.plantspot.backend.sunlight.SunlightCalculator.Cell;
import com.plantspot.backend.sunlight.SunlightCalculator.WindowGeom;

/**
 * 방 평면도 + 칸 색 + 창문 + 표시점을 PNG로 그린다 (글자 없이 그려 글꼴과 무관). [필수: 파일 다운로드 — 계절별 배치도 이미지]
 */
public final class RoomPlanRenderer {

	private RoomPlanRenderer() {
	}

	public record Mark(double x, double y, Color color) {
	}

	public static final Color SUN = new Color(245, 180, 0);

	/** 직사광 시간 색 (노랄수록 오래), 직사광 없으면 하늘빛 밝기에 따라 회청색 */
	public static Function<Cell, Color> directColors(List<Cell> cells) {
		int max = Math.max(60, cells.stream().mapToInt(Cell::directMinutes).max().orElse(0));
		return c -> {
			if (c.directMinutes() > 0) {
				int alpha = (int) (40 + 200.0 * c.directMinutes() / max);
				return new Color(245, 180, 0, Math.min(255, alpha));
			}
			int alpha = (int) Math.max(0, Math.min(120, 120 - c.lux() / 15));
			return new Color(80, 110, 140, alpha);
		};
	}

	public static Function<Cell, Color> levelColors() {
		return c -> c.level() == LightLevel.BRIGHT ? new Color(245, 180, 0)
				: c.level() == LightLevel.MEDIUM ? new Color(140, 192, 132) : new Color(91, 107, 122);
	}

	/** 점수 0~100 → 빨강~초록 */
	public static Color scoreColor(double score) {
		float hue = (float) (Math.max(0, Math.min(120, score * 1.2)) / 360.0);
		Color c = Color.getHSBColor(hue, 0.7f, 0.8f);
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 200);
	}

	public static BufferedImage render(List<Point> polygon, double orientationDeg, List<WindowGeom> windows,
			List<Cell> cells, double cellSize, Function<Cell, Color> color, List<Mark> marks, int sizePx) {
		double minX = polygon.stream().mapToDouble(Point::x).min().orElse(0);
		double maxX = polygon.stream().mapToDouble(Point::x).max().orElse(1);
		double minY = polygon.stream().mapToDouble(Point::y).min().orElse(0);
		double maxY = polygon.stream().mapToDouble(Point::y).max().orElse(1);
		double w = maxX - minX;
		double h = maxY - minY;
		double pad = Math.max(w, h) * 0.12;
		double scale = sizePx / (Math.max(w, h) + pad * 2);
		int width = (int) Math.ceil((w + pad * 2) * scale);
		int height = (int) Math.ceil((h + pad * 2) * scale);

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		AffineTransform base = g.getTransform();
		g.translate(pad * scale - minX * scale, pad * scale - minY * scale);
		g.scale(scale, scale);

		Path2D.Double room = new Path2D.Double();
		for (int i = 0; i < polygon.size(); i++) {
			Point p = polygon.get(i);
			if (i == 0) {
				room.moveTo(p.x(), p.y());
			} else {
				room.lineTo(p.x(), p.y());
			}
		}
		room.closePath();
		g.setColor(new Color(247, 248, 244));
		g.fill(room);

		g.setClip(room);
		for (Cell c : cells) {
			g.setColor(color.apply(c));
			g.fill(new Rectangle2D.Double(c.x() - cellSize / 2, c.y() - cellSize / 2, cellSize, cellSize));
		}
		g.setColor(new Color(217, 223, 211));
		g.setStroke(new BasicStroke((float) (0.6 / scale)));
		for (Cell c : cells) {
			g.draw(new Rectangle2D.Double(c.x() - cellSize / 2, c.y() - cellSize / 2, cellSize, cellSize));
		}
		g.setClip(null);

		g.setColor(new Color(31, 42, 31));
		g.setStroke(new BasicStroke((float) (3 / scale)));
		g.draw(room);
		g.setColor(new Color(74, 163, 223));
		g.setStroke(new BasicStroke((float) (7 / scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		for (WindowGeom wg : windows) {
			g.draw(new Line2D.Double(wg.startX(), wg.startY(), wg.startX() + wg.alongX() * wg.width(),
					wg.startY() + wg.alongY() * wg.width()));
		}
		double r = cellSize * 0.45;
		for (Mark m : marks) {
			g.setColor(m.color());
			g.fill(new Ellipse2D.Double(m.x() - r, m.y() - r, r * 2, r * 2));
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke((float) (2 / scale)));
			g.draw(new Ellipse2D.Double(m.x() - r, m.y() - r, r * 2, r * 2));
		}

		// 북쪽 화살표 (왼쪽 위)
		g.setTransform(base);
		double cx = pad * scale * 0.55;
		double cy = pad * scale * 0.55;
		double len = Math.max(14, pad * scale * 0.4);
		double a = Math.toRadians(-orientationDeg);
		double tx = cx + Math.sin(a) * len;
		double ty = cy - Math.cos(a) * len;
		g.setColor(new Color(47, 125, 74));
		g.setStroke(new BasicStroke(3));
		g.draw(new Line2D.Double(cx - Math.sin(a) * len * 0.6, cy + Math.cos(a) * len * 0.6, tx, ty));
		Path2D.Double head = new Path2D.Double();
		head.moveTo(tx, ty);
		head.lineTo(tx - Math.sin(a - 0.5) * len * 0.45, ty + Math.cos(a - 0.5) * len * 0.45);
		head.lineTo(tx - Math.sin(a + 0.5) * len * 0.45, ty + Math.cos(a + 0.5) * len * 0.45);
		head.closePath();
		g.fill(head);
		g.dispose();
		return img;
	}

	public static byte[] png(BufferedImage img) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
