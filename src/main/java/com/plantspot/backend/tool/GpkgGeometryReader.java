package com.plantspot.backend.tool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * GeoPackage 도형 바이너리(GP 헤더 + WKB) 중 Polygon / MultiPolygon을 읽는다.
 * Z·M 좌표가 있으면 읽고 버린다. 도형은 "링 목록의 목록"으로 돌려준다: polygons[i][0] = 바깥 링, 나머지 = 구멍.
 */
public final class GpkgGeometryReader {

	private GpkgGeometryReader() {
	}

	/** 링 하나 = 점 목록, 점 = {x, y}. */
	public record Polygon(List<List<double[]>> rings) {

		public List<double[]> outer() {
			return rings.get(0);
		}
	}

	public static List<Polygon> read(byte[] blob) {
		if (blob == null || blob.length < 8 || blob[0] != 'G' || blob[1] != 'P') {
			throw new IllegalArgumentException("GeoPackage 도형이 아닙니다.");
		}
		int flags = blob[3] & 0xFF;
		if ((flags & 0b0001_0000) != 0) {
			return List.of(); // 빈 도형
		}
		int envelopeIndicator = (flags >> 1) & 0b111;
		int envelopeBytes = switch (envelopeIndicator) {
			case 0 -> 0;
			case 1 -> 32;
			case 2, 3 -> 48;
			case 4 -> 64;
			default -> throw new IllegalArgumentException("알 수 없는 envelope 종류: " + envelopeIndicator);
		};
		int wkbStart = 8 + envelopeBytes;
		ByteBuffer buf = ByteBuffer.wrap(blob, wkbStart, blob.length - wkbStart);
		List<Polygon> result = new ArrayList<>();
		readGeometry(buf, result);
		return result;
	}

	private static void readGeometry(ByteBuffer buf, List<Polygon> out) {
		buf.order(buf.get() == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		long rawType = Integer.toUnsignedLong(buf.getInt());

		// ISO WKB(1000/2000/3000 더하기)와 EWKB(상위 비트) 표기를 모두 처리
		boolean ewkbZ = (rawType & 0x8000_0000L) != 0;
		boolean ewkbM = (rawType & 0x4000_0000L) != 0;
		long type = rawType & 0x0FFF_FFFFL;
		int iso = (int) (type / 1000);
		int base = (int) (type % 1000);
		int dims = 2 + ((iso == 1 || iso == 3 || ewkbZ) ? 1 : 0) + ((iso == 2 || iso == 3 || ewkbM) ? 1 : 0);

		switch (base) {
			case 3 -> out.add(readPolygon(buf, dims));
			case 6 -> {
				int count = buf.getInt();
				for (int i = 0; i < count; i++) {
					readGeometry(buf, out);
				}
			}
			default -> throw new IllegalArgumentException("Polygon/MultiPolygon이 아닌 도형입니다 (WKB type " + rawType + ")");
		}
	}

	private static Polygon readPolygon(ByteBuffer buf, int dims) {
		int ringCount = buf.getInt();
		List<List<double[]>> rings = new ArrayList<>(ringCount);
		for (int r = 0; r < ringCount; r++) {
			int pointCount = buf.getInt();
			List<double[]> ring = new ArrayList<>(pointCount);
			for (int p = 0; p < pointCount; p++) {
				double x = buf.getDouble();
				double y = buf.getDouble();
				for (int d = 2; d < dims; d++) {
					buf.getDouble();
				}
				ring.add(new double[] { x, y });
			}
			rings.add(ring);
		}
		return new Polygon(rings);
	}

	// ----- 도형 계산 -----

	/** 넓이와 중심점. */
	public record Shape(double area, double centerX, double centerY) {
	}

	/** 구멍을 뺀 전체 넓이와 넓이 가중 중심점. */
	public static Shape shape(List<Polygon> polygons) {
		double area = 0;
		double cx = 0;
		double cy = 0;
		for (Polygon polygon : polygons) {
			for (int r = 0; r < polygon.rings().size(); r++) {
				List<double[]> ring = polygon.rings().get(r);
				double[] ringResult = ringAreaAndMoment(ring);
				// 링 방향과 상관없이 바깥 링은 더하고 구멍은 뺀다.
				double sign = (r == 0 ? 1 : -1) * Math.signum(ringResult[0]);
				area += sign * ringResult[0];
				cx += sign * ringResult[1];
				cy += sign * ringResult[2];
			}
		}
		if (area == 0) {
			// 넓이가 0인 이상한 도형은 점들의 평균으로
			double sx = 0, sy = 0;
			int n = 0;
			for (Polygon polygon : polygons) {
				for (double[] p : polygon.outer()) {
					sx += p[0];
					sy += p[1];
					n++;
				}
			}
			return n == 0 ? new Shape(0, 0, 0) : new Shape(0, sx / n, sy / n);
		}
		return new Shape(area, cx / (6 * area), cy / (6 * area));
	}

	/** {부호 있는 넓이, x 모멘트, y 모멘트} — 신발끈 공식. 점 좌표가 커서 첫 점 기준으로 옮겨 계산한다. */
	private static double[] ringAreaAndMoment(List<double[]> ring) {
		int n = ring.size();
		if (n < 3) {
			return new double[] { 0, 0, 0 };
		}
		double ox = ring.get(0)[0];
		double oy = ring.get(0)[1];
		double a = 0, mx = 0, my = 0;
		for (int i = 0; i < n; i++) {
			double[] p = ring.get(i);
			double[] q = ring.get((i + 1) % n);
			double x0 = p[0] - ox, y0 = p[1] - oy, x1 = q[0] - ox, y1 = q[1] - oy;
			double cross = x0 * y1 - x1 * y0;
			a += cross;
			mx += (x0 + x1) * cross;
			my += (y0 + y1) * cross;
		}
		a /= 2;
		// 원점을 옮긴 만큼 모멘트를 되돌린다: Σ(x+ox)·cross = mx + ox·6a
		return new double[] { a, mx + ox * 6 * a, my + oy * 6 * a };
	}

	/** WKT MULTIPOLYGON (소수 둘째 자리, cm 단위). */
	public static String toWkt(List<Polygon> polygons) {
		if (polygons.isEmpty()) {
			return "MULTIPOLYGON EMPTY";
		}
		return polygons.stream()
				.map(p -> p.rings().stream()
						.map(ring -> ring.stream()
								.map(pt -> String.format(Locale.ROOT, "%.2f %.2f", pt[0], pt[1]))
								.collect(Collectors.joining(", ", "(", ")")))
						.collect(Collectors.joining(", ", "(", ")")))
				.collect(Collectors.joining(", ", "MULTIPOLYGON (", ")"));
	}
}
