package com.plantspot.backend.obstruction;

import java.util.List;

import com.plantspot.backend.geo.Wkt;

/**
 * 관측점(우리 집 창 높이)에서 본 방향별 가림 각도(스카이라인)를 건물 외곽선·높이로 계산한다.
 * 가림 각도 = atan((건물 높이 − 눈 높이) / 수평 거리). 방향은 북 0 / 동 90 / 남 180 / 서 270, BIN_DEG 간격.
 * 외곽선 변을 0.25m 간격으로 잘라 각 점의 방향·각도를 구하고 칸마다 가장 큰 값을 남긴다.
 * 한계: 땅 높낮이(경사지)는 반영하지 않는다 → 센서 실측으로 보정.
 */
public final class ObstructionCalculator {

	public static final int BIN_DEG = 5;
	public static final int BINS = 360 / BIN_DEG;
	private static final double SAMPLE_STEP_M = 0.25;
	/** 1m 안쪽은 관측점과 붙은 건물로 보고 가림 각도를 이 값으로 둔다 */
	private static final double MIN_DISTANCE_M = 1.0;

	private ObstructionCalculator() {
	}

	/** 가림물 하나. footprintWkt가 없으면 중심점 하나로 본다. */
	public record Obstacle(long id, double height, double centerX, double centerY, String footprintWkt) {
	}

	/** 칸별 결과: angles[i] = i×BIN_DEG ~ (i+1)×BIN_DEG 방향의 가림 각도(도), causeIds[i] = 원인 건물(없으면 -1). */
	public record Skyline(double[] angles, long[] causeIds, double[] distances) {
	}

	public static Skyline calculate(double observerX, double observerY, double eyeHeight, List<Obstacle> obstacles) {
		double[] angles = new double[BINS];
		long[] causes = new long[BINS];
		double[] distances = new double[BINS];
		java.util.Arrays.fill(causes, -1);

		for (Obstacle o : obstacles) {
			double rise = o.height() - eyeHeight;
			if (rise <= 0) {
				continue;
			}
			List<List<double[]>> rings = Wkt.rings(o.footprintWkt());
			if (rings.isEmpty()) {
				apply(observerX, observerY, rise, o.centerX(), o.centerY(), o.id(), angles, causes, distances);
				continue;
			}
			for (List<double[]> ring : rings) {
				for (int k = 0; k + 1 < ring.size(); k++) {
					double[] a = ring.get(k);
					double[] b = ring.get(k + 1);
					double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
					int steps = Math.max(1, (int) Math.ceil(len / SAMPLE_STEP_M));
					for (int s = 0; s <= steps; s++) {
						double t = (double) s / steps;
						apply(observerX, observerY, rise, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, o.id(),
								angles, causes, distances);
					}
				}
			}
		}
		return new Skyline(angles, causes, distances);
	}

	private static void apply(double ox, double oy, double rise, double px, double py, long id, double[] angles,
			long[] causes, double[] distances) {
		double dx = px - ox;
		double dy = py - oy;
		double dist = Math.max(Math.hypot(dx, dy), MIN_DISTANCE_M);
		double azimuth = Math.toDegrees(Math.atan2(dx, dy));
		if (azimuth < 0) {
			azimuth += 360;
		}
		int bin = Math.min(BINS - 1, (int) (azimuth / BIN_DEG));
		double angle = Math.toDegrees(Math.atan2(rise, dist));
		if (angle > angles[bin]) {
			angles[bin] = angle;
			causes[bin] = id;
			distances[bin] = dist;
		}
	}

	/** 방위각이 속한 칸 번호. */
	public static int binOf(double azimuthDeg) {
		double a = azimuthDeg % 360;
		if (a < 0) {
			a += 360;
		}
		return Math.min(BINS - 1, (int) (a / BIN_DEG));
	}
}
