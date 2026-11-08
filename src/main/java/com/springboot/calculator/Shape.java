package com.springboot.calculator;

import com.springboot.domain.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 방 모양(다각형) 계산과 DB 저장 형식("x,y;x,y;...") 변환. */
public final class Shape {

	private Shape() {
	}

	public static String encode(List<Point> points) {
		return points.stream()
				.map(p -> String.format(Locale.ROOT, "%.1f,%.1f", p.x(), p.y()))
				.collect(Collectors.joining(";"));
	}

	public static List<Point> decode(String text) {
		List<Point> points = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return points;
		}
		for (String pair : text.split(";")) {
			String[] xy = pair.split(",");
			points.add(new Point(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
		}
		return points;
	}

	/** 부호 있는 넓이(cm²). y가 아래로 +인 좌표에서 화면상 시계 방향이면 양수. */
	public static double signedArea(List<Point> points) {
		double sum = 0;
		for (int i = 0; i < points.size(); i++) {
			Point p = points.get(i);
			Point q = points.get((i + 1) % points.size());
			sum += p.x() * q.y() - q.x() * p.y();
		}
		return sum / 2;
	}

	public static double wallLength(List<Point> points, int wallIndex) {
		Point a = points.get(wallIndex);
		Point b = points.get((wallIndex + 1) % points.size());
		return Math.hypot(b.x() - a.x(), b.y() - a.y());
	}

	/**
	 * 벽(wallIndex번 점 → 다음 점)이 바깥으로 향하는 방위각(도, 북 0 / 동 90 / 남 180 / 서 270).
	 *
	 * @param orientationDeg 평면도 위쪽(-y)이 가리키는 방위각
	 */
	public static double wallAzimuth(List<Point> points, int wallIndex, double orientationDeg) {
		Point a = points.get(wallIndex);
		Point b = points.get((wallIndex + 1) % points.size());
		double dx = b.x() - a.x();
		double dy = b.y() - a.y();
		// 바깥쪽 법선: 화면상 시계 방향 다각형이면 (dy, -dx)
		double nx = signedArea(points) > 0 ? dy : -dy;
		double ny = signedArea(points) > 0 ? -dx : dx;
		double fromUp = Math.toDegrees(Math.atan2(nx, -ny));
		return normalize(orientationDeg + fromUp);
	}

	public static double normalize(double deg) {
		double r = deg % 360;
		return r < 0 ? r + 360 : r;
	}

	/** 방위각 → "남향" 같은 8방위 이름. */
	public static String directionName(double azimuthDeg) {
		String[] names = { "북", "북동", "동", "남동", "남", "남서", "서", "북서" };
		return names[(int) Math.round(normalize(azimuthDeg) / 45.0) % 8] + "향";
	}

	/** 자기 자신과 교차하지 않는 다각형인지 (이웃하지 않은 변끼리 교차 검사). */
	public static boolean isSimple(List<Point> points) {
		int n = points.size();
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (j == i + 1 || (i == 0 && j == n - 1)) {
					continue;
				}
				if (segmentsIntersect(points.get(i), points.get((i + 1) % n), points.get(j), points.get((j + 1) % n))) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean segmentsIntersect(Point p1, Point p2, Point p3, Point p4) {
		double d1 = cross(p3, p4, p1);
		double d2 = cross(p3, p4, p2);
		double d3 = cross(p1, p2, p3);
		double d4 = cross(p1, p2, p4);
		return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0)) && d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0;
	}

	private static double cross(Point a, Point b, Point c) {
		return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
	}
}
