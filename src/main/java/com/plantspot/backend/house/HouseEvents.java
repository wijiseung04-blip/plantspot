package com.plantspot.backend.house;

/**
 * 집·방이 바뀌거나 지워질 때 알리는 이벤트. 같은 트랜잭션 안에서 바로 처리된다.
 * 가림 각도·칸별 햇빛·추천처럼 집/방에 딸린 계산 결과를 지우거나 다시 계산하게 하는 용도.
 */
public final class HouseEvents {

	private HouseEvents() {
	}

	/** 층·위치가 바뀌었거나 가림 각도가 바뀜 → 집의 모든 방 계산 결과가 낡음 */
	public record HouseChanged(Long houseId) {
	}

	/** 집을 지우기 직전 */
	public record HouseDeleting(Long houseId) {
	}

	/** 방 모양·창문·커튼이 바뀜 → 그 방 계산 결과가 낡음 */
	public record RoomChanged(Long roomId) {
	}

	/** 방을 지우기 직전 */
	public record RoomDeleting(Long roomId) {
	}
}
