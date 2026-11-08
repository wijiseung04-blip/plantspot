package com.plantspot.backend.plant;

/** 식물 종 이벤트. */
public final class PlantEvents {

	private PlantEvents() {
	}

	/** 관리자가 식물을 지우기 직전 → 추천·내 식물 연결 정리 */
	public record PlantDeleting(Long plantId) {
	}

	/** 변환값이 바뀜 → 추천 결과가 낡음 */
	public record PlantChanged(Long plantId) {
	}
}
