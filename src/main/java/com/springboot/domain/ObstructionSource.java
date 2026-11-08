package com.springboot.domain;

/** 가림 각도 출처. */
public enum ObstructionSource {
	/** 공공 건물 데이터(높이·외곽선)로 계산 */
	BUILDING,
	/** 창틀 센서 실측(해가 가림물 위로 올라온 시각의 해 고도) — 건물 데이터 값을 덮어쓴다 */
	SENSOR
}
