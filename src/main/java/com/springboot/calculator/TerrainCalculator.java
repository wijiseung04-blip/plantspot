package com.springboot.calculator;

/**
 * 땅 높낮이(산)가 만드는 방향별 가림 각도. [① 공개 데이터(고도) 분석]
 * 5° 방향마다 관측점에서 바깥으로 격자 간격의 절반씩 나가며 각도 = atan((땅 높이 − 지구 곡률 낮아짐 − 눈 높이) / 거리)의 최대값.
 * 지구 곡률 낮아짐 = 거리² / (2R) × (1 − 0.13), 0.13 = 대기 굴절 계수.
 * 가까운 곳(건물을 보는 반경 안)은 건물 데이터가 맡는다. 고도 자료(Copernicus DEM)는 건물·나무 높이가 섞인 표면 높이라
 * 가까운 곳까지 보면 같은 건물을 두 번 세게 된다.
 */
public final class TerrainCalculator {

	private static final double EARTH_RADIUS_M = 6_371_000;
	private static final double REFRACTION = 0.13;

	private TerrainCalculator() {
	}

	/** 방향별 지형 가림 각도(도)와 그 지점까지의 거리(m). */
	public record TerrainSkyline(double[] angles, double[] distances) {
	}

	/**
	 * @param eyeElevation 눈 높이(해발 m) = 우리 집 땅 높이 + 층 높이 + 1.2
	 * @param fromM 이 거리부터 본다
	 * @param toM 이 거리까지 본다 (격자 밖으로 나가면 거기서 멈춤)
	 */
	public static TerrainSkyline calculate(TerrainModel model, double x, double y, double eyeElevation, double fromM,
			double toM) {
		int bins = ObstructionCalculator.BINS;
		double[] angles = new double[bins];
		double[] distances = new double[bins];
		double step = model.spacing() / 2;
		for (int i = 0; i < bins; i++) {
			// 칸 안 세 방향(시작·가운데·끝 근처)을 보고 가장 큰 값
			for (double off : new double[] { 0.5, 2.5, 4.5 }) {
				double az = Math.toRadians(i * ObstructionCalculator.BIN_DEG + off);
				double sx = Math.sin(az);
				double sy = Math.cos(az);
				for (double d = fromM; d <= toM; d += step) {
					double z = model.elevationAt(x + sx * d, y + sy * d);
					if (Double.isNaN(z)) {
						break;
					}
					double drop = d * d / (2 * EARTH_RADIUS_M) * (1 - REFRACTION);
					double angle = Math.toDegrees(Math.atan2(z - drop - eyeElevation, d));
					if (angle > angles[i]) {
						angles[i] = angle;
						distances[i] = d;
					}
				}
			}
		}
		return new TerrainSkyline(angles, distances);
	}
}
