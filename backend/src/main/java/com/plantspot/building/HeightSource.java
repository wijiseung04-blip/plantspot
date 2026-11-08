package com.plantspot.building;

/** 건물 최종 높이를 어디서 얻었는지. */
public enum HeightSource {
	/** GIS 높이(A16) 값을 그대로 사용 */
	MEASURED,
	/** 지상층수 × 용도별 층고로 추정 */
	FLOOR_ESTIMATE,
	/** 높이·층수가 모두 없어 기본값(3.5m) 사용 */
	DEFAULT
}
