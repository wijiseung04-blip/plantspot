package com.springboot.calculator;

/**
 * 해 위치.
 *
 * @param altitudeDeg 고도(도). 지평선 0, 천정 90, 음수면 지평선 아래. 대기 굴절은 반영하지 않은 기하학적 값
 * @param azimuthDeg  방위각(도). 북 0, 동 90, 남 180, 서 270 (시계 방향)
 */
public record SolarPosition(double altitudeDeg, double azimuthDeg) {

	public boolean aboveHorizon() {
		return altitudeDeg > 0;
	}
}
