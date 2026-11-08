package com.springboot.calculator;

/**
 * 계산에 쓰는 방향별 가림 각도. 칸마다 건물 데이터 값, 센서 실측 값(없으면 NaN)을 갖고
 * 실제로 쓰는 값은 센서 값이 있으면 센서, 없으면 건물 데이터.
 */
public record ObstructionProfile(double[] building, double[] sensor) {

	public static ObstructionProfile empty() {
		double[] sensor = new double[ObstructionCalculator.BINS];
		java.util.Arrays.fill(sensor, Double.NaN);
		return new ObstructionProfile(new double[ObstructionCalculator.BINS], sensor);
	}

	/** 보정 반영 값. */
	public double angleAt(double azimuthDeg) {
		int bin = ObstructionCalculator.binOf(azimuthDeg);
		return Double.isNaN(sensor[bin]) ? building[bin] : sensor[bin];
	}

	/** 보정 전(건물 데이터만) 값. */
	public double buildingAngleAt(double azimuthDeg) {
		return building[ObstructionCalculator.binOf(azimuthDeg)];
	}

	public boolean hasSensorData() {
		for (double v : sensor) {
			if (!Double.isNaN(v)) {
				return true;
			}
		}
		return false;
	}

	/** 센서 값을 무시한(보정 전) 프로필. */
	public ObstructionProfile withoutSensor() {
		return new ObstructionProfile(building, empty().sensor());
	}

	/** 방향 범위(중심 ± halfWidth) 안의 평균 가림 각도 — 창 밖 하늘이 얼마나 열려 있는지 볼 때. */
	public double averageAngle(double centerAzimuth, double halfWidth) {
		double sum = 0;
		int n = 0;
		for (double a = centerAzimuth - halfWidth; a <= centerAzimuth + halfWidth; a += ObstructionCalculator.BIN_DEG) {
			sum += angleAt(a);
			n++;
		}
		return n == 0 ? 0 : sum / n;
	}
}
