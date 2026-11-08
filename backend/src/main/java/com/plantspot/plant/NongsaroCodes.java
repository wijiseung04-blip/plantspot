package com.plantspot.plant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 농사로 실내정원용 식물 코드표 (매뉴얼 기준).
 * 광요구도·배치장소·겨울 최저온도를 우리 서비스 기준(lux, 창에서 거리 cm, ℃)으로 바꾸는 기본 변환표.
 */
public final class NongsaroCodes {

	private NongsaroCodes() {
	}

	/** 광요구도 코드 → {최소 lux, 최대 lux} */
	public static final Map<String, int[]> LIGHT = Map.of(
			"055001", new int[] { 300, 800 },
			"055002", new int[] { 800, 1500 },
			"055003", new int[] { 1500, 10000 });

	public static final Map<String, String> LIGHT_LABEL = Map.of(
			"055001", "낮은 광도(300~800 lux)",
			"055002", "중간 광도(800~1,500 lux)",
			"055003", "높은 광도(1,500~10,000 lux)");

	/** 배치장소 코드 → {창에서 최소 거리 cm, 최대 거리 cm}. 거리와 관계없는 코드(습한 곳 등)는 없음 */
	public static final Map<String, int[]> PLACEMENT = Map.of(
			"064005", new int[] { 0, 50 },
			"064004", new int[] { 50, 150 },
			"064003", new int[] { 150, 300 },
			"064002", new int[] { 300, 500 },
			"064001", new int[] { 500, 2000 });

	public static final Map<String, String> PLACEMENT_LABEL = Map.of(
			"064005", "발코니 창측(0~50cm)",
			"064004", "발코니 내측(50~150cm)",
			"064003", "거실 창측(150~300cm)",
			"064002", "거실 내측(300~500cm)",
			"064001", "실내 어두운 곳(500cm 이상)",
			"064006", "습한 곳",
			"064007", "넓은 곳",
			"064008", "좁은 곳");

	/** 겨울 최저온도 코드 → ℃ (이 온도보다 낮아지면 경고) */
	public static final Map<String, Integer> WINTER_MIN = Map.of(
			"057001", 0,
			"057002", 5,
			"057003", 7,
			"057004", 10,
			"057005", 13);

	public static List<String> split(String codes) {
		if (codes == null || codes.isBlank()) {
			return List.of();
		}
		return Arrays.stream(codes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
