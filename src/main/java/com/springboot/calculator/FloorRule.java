package com.springboot.calculator;

import com.springboot.domain.Building;
import com.springboot.domain.HeightSource;

/**
 * 건물에서 고를 수 있는 가장 높은 층. [필수: 검증 — 건물 데이터와 맞지 않는 층 막기]
 * <ul>
 * <li>지상층수(A26)가 있으면 그 값</li>
 * <li>없고 높이가 실측(MEASURED)이면 높이 ÷ 2.5m 올림 (2.5m 는 가장 낮게 잡은 층고라 실제 층이 막히지 않는다)</li>
 * <li>둘 다 없으면(높이 기본값 3.5m 등) 모름 → 막지 않고 경고만</li>
 * </ul>
 */
public final class FloorRule {

	/** 높이로 층을 짐작할 때 쓰는 가장 낮은 층고(m) */
	public static final double MIN_FLOOR_HEIGHT = 2.5;

	private FloorRule() {
	}

	/** 고를 수 있는 가장 높은 층, 모르면 null. */
	public static Integer maxFloor(Building b) {
		if (b == null) {
			return null;
		}
		if (b.getGroundFloors() != null && b.getGroundFloors() > 0) {
			return b.getGroundFloors();
		}
		if (b.getHeightSource() == HeightSource.MEASURED && b.getHeight() > 0) {
			return Math.max(1, (int) Math.ceil(b.getHeight() / MIN_FLOOR_HEIGHT - 1e-9));
		}
		return null;
	}

	/** 층이 건물 범위를 넘으면 오류 메시지, 괜찮으면 null. */
	public static String violation(Building b, int floor) {
		Integer max = maxFloor(b);
		if (max == null || floor <= max) {
			return null;
		}
		String name = b.getDongLabel() != null && !b.getDongLabel().isBlank() ? b.getDongLabel() + " " : "";
		return b.getGroundFloors() != null && b.getGroundFloors() > 0
				? String.format("%s건물은 지상 %d층입니다. 층을 %d 이하로 입력하세요.", name, max, max)
				: String.format("%s건물 높이(%.1fm)로 보면 %d층을 넘을 수 없습니다. 층을 %d 이하로 입력하세요.", name, b.getHeight(),
						max, max);
	}

	/** 층수를 확인할 수 없을 때 안내, 확인되면 null. */
	public static String warning(Building b) {
		return maxFloor(b) == null ? "건물 층수 정보가 없어 입력한 층을 확인하지 못했습니다." : null;
	}
}
