package com.springboot.calculator;

import com.springboot.domain.HeightSource;

/**
 * GIS 건물통합정보의 높이·층수·용도로 건물 최종 높이를 정한다.
 * <ol>
 * <li>높이 &gt; 0, 높이 ≤ 200m, (층수가 있으면) 높이/층수 ≤ 10m → 높이 그대로 (MEASURED)</li>
 * <li>아니고 층수가 있으면 → 층수 × 층고 (공동주택 3.0, 단독주택 3.5, 그 외 3.6) (FLOOR_ESTIMATE)</li>
 * <li>둘 다 없으면 → 3.5m (DEFAULT)</li>
 * </ol>
 * 높이 15,400m 같은 입력 오류는 1번에서 걸러진다.
 */
public final class HeightRule {

	public static final double MAX_VALID_HEIGHT = 200.0;
	public static final double MAX_HEIGHT_PER_FLOOR = 10.0;
	public static final double DEFAULT_HEIGHT = 3.5;

	private HeightRule() {
	}

	public static Result decide(Double rawHeight, Integer groundFloors, String usage) {
		boolean hasFloors = groundFloors != null && groundFloors > 0;
		if (rawHeight != null && rawHeight > 0 && rawHeight <= MAX_VALID_HEIGHT
				&& (!hasFloors || rawHeight / groundFloors <= MAX_HEIGHT_PER_FLOOR)) {
			return new Result(rawHeight, HeightSource.MEASURED);
		}
		if (hasFloors) {
			return new Result(groundFloors * storeyHeight(usage), HeightSource.FLOOR_ESTIMATE);
		}
		return new Result(DEFAULT_HEIGHT, HeightSource.DEFAULT);
	}

	/** 용도별 층고(m). */
	static double storeyHeight(String usage) {
		if (usage != null) {
			if (usage.contains("공동주택")) {
				return 3.0;
			}
			if (usage.contains("단독주택")) {
				return 3.5;
			}
		}
		return 3.6;
	}

	public record Result(double height, HeightSource source) {
	}
}
