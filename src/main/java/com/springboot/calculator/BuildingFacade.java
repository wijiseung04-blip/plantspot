package com.springboot.calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 우리 건물 외곽선에서 창마다 관측점(창 바로 바깥 한 점)을 정한다. [① 분석: 창별 가림 각도]
 * <ol>
 * <li>긴 축: 외곽선을 감싸는 가장 작은 사각형의 긴 변 방향 (0~180°)</li>
 * <li>세대 위치 position(−1 = 축의 반대쪽 끝 ~ 0 = 가운데 ~ +1 = 축 방향 끝)만큼 긴 축을 따라 옮긴 점에서</li>
 * <li>창 방향으로 나가 처음 만나는 외벽 + 0.5m 가 관측점</li>
 * </ol>
 * 외곽선이 없으면 건물 중심점을 그대로 쓴다.
 */
public final class BuildingFacade {

	/** 외벽에서 이만큼 바깥(m) */
	private static final double OUTSIDE_M = 0.5;

	private BuildingFacade() {
	}

	/** 관측점 {x, y}와 중심에서 떨어진 거리. */
	public record Observer(double x, double y, double offsetFromCenter) {
	}

	/** 긴 축 방향(도, 0 이상 180 미만, 북 0 / 동 90). 외곽선이 없으면 NaN. */
	public static double longAxis(String wkt) {
		List<double[]> pts = points(Wkt.rings(wkt));
		if (pts.size() < 3) {
			return Double.NaN;
		}
		double bestArea = Double.MAX_VALUE;
		double bestAxis = 0;
		for (int deg = 0; deg < 90; deg++) {
			double[] u = unit(deg);
			double[] v = unit(deg + 90);
			double[] a = extent(pts, u, 0, 0);
			double[] b = extent(pts, v, 0, 0);
			double lu = a[1] - a[0];
			double lv = b[1] - b[0];
			double area = lu * lv;
			if (area < bestArea - 1e-9) {
				bestArea = area;
				bestAxis = lu >= lv ? deg : deg + 90;
			}
		}
		return bestAxis % 180;
	}

	public static Observer observer(String wkt, double cx, double cy, double windowAzimuth, double position) {
		List<List<double[]>> rings = Wkt.rings(wkt);
		List<double[]> pts = points(rings);
		if (pts.size() < 3) {
			return new Observer(cx, cy, 0);
		}
		double axis = longAxis(wkt);
		double[] u = unit(axis);
		double[] ext = extent(pts, u, cx, cy);
		double p = Math.max(-1, Math.min(1, position));
		double along = (ext[0] + ext[1]) / 2 + p * (ext[1] - ext[0]) / 2 * 0.95;
		double sx = cx + u[0] * along;
		double sy = cy + u[1] * along;

		double[] d = unit(windowAzimuth);
		List<Double> hits = new ArrayList<>();
		for (List<double[]> ring : rings) {
			for (int k = 0; k + 1 < ring.size(); k++) {
				double t = rayHit(sx, sy, d, ring.get(k), ring.get(k + 1));
				if (t >= 0) {
					hits.add(t);
				}
			}
		}
		hits.sort(Double::compare);
		double t;
		if (hits.isEmpty()) {
			t = 0;
		} else if (inside(rings, sx, sy)) {
			t = hits.get(0); // 안에서 처음 만나는 벽 = 나가는 벽
		} else {
			// 오목한 건물에서 시작점이 바깥이면: 들어갔다가 다시 나가는 벽, 없으면 시작점
			t = hits.size() >= 2 ? hits.get(1) : 0;
		}
		double ox = sx + d[0] * (t + OUTSIDE_M);
		double oy = sy + d[1] * (t + OUTSIDE_M);
		return new Observer(ox, oy, Math.hypot(ox - cx, oy - cy));
	}

	/** 방향(도)의 단위 벡터 (x = 동, y = 북). */
	static double[] unit(double deg) {
		double r = Math.toRadians(deg);
		return new double[] { Math.sin(r), Math.cos(r) };
	}

	/** 점들을 u 축에 투영한 범위 [min, max] (기준점 (ox, oy)). */
	private static double[] extent(List<double[]> pts, double[] u, double ox, double oy) {
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (double[] q : pts) {
			double s = (q[0] - ox) * u[0] + (q[1] - oy) * u[1];
			min = Math.min(min, s);
			max = Math.max(max, s);
		}
		return new double[] { min, max };
	}

	/** 반직선 (s + t·d)과 선분 a-b가 만나는 t (안 만나면 −1). */
	private static double rayHit(double sx, double sy, double[] d, double[] a, double[] b) {
		double ex = b[0] - a[0];
		double ey = b[1] - a[1];
		double den = d[0] * ey - d[1] * ex;
		if (Math.abs(den) < 1e-12) {
			return -1;
		}
		double ax = a[0] - sx;
		double ay = a[1] - sy;
		double t = (ax * ey - ay * ex) / den;
		double s = (ax * d[1] - ay * d[0]) / den;
		return t >= 0 && s >= 0 && s <= 1 ? t : -1;
	}

	private static boolean inside(List<List<double[]>> rings, double x, double y) {
		boolean in = false;
		for (List<double[]> ring : rings) {
			for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
				double[] a = ring.get(i);
				double[] b = ring.get(j);
				if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
					in = !in;
				}
			}
		}
		return in;
	}

	private static List<double[]> points(List<List<double[]>> rings) {
		List<double[]> all = new ArrayList<>();
		rings.forEach(all::addAll);
		return all;
	}
}
