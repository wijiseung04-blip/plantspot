package com.plantspot.plant;

import java.util.List;

/**
 * 농사로 코드 → 추천 기준값 변환 (기본 변환표).
 * <ul>
 * <li>lux: 광요구도 코드 범위들의 최소~최대</li>
 * <li>직사광: 높은 광도만이면 최소 2시간, 낮은 광도만이면 최대 1시간(강한 직사광에 잎이 탐), 중간이 최대면 최대 3시간</li>
 * <li>창에서 거리: 배치장소 코드 범위들의 최소~최대 (거리와 무관한 코드는 무시)</li>
 * <li>겨울 최저온도: 코드 → ℃</li>
 * </ul>
 * 관리자가 식물별로 고칠 수 있다.
 */
public final class PlantConverter {

	private PlantConverter() {
	}

	public record Converted(int minLux, int maxLux, int minDirectMinutes, Integer maxDirectMinutes,
			Integer minWindowDistance, Integer maxWindowDistance, Integer winterMinTemp) {
	}

	public static Converted convert(String lightCodes, String placementCodes, String winterMinCode) {
		List<String> lights = NongsaroCodes.split(lightCodes).stream().filter(NongsaroCodes.LIGHT::containsKey).toList();
		int minLux = lights.stream().mapToInt(c -> NongsaroCodes.LIGHT.get(c)[0]).min().orElse(300);
		int maxLux = lights.stream().mapToInt(c -> NongsaroCodes.LIGHT.get(c)[1]).max().orElse(10000);

		boolean low = lights.contains("055001");
		boolean mid = lights.contains("055002");
		boolean high = lights.contains("055003");
		int minDirect = high && !low && !mid ? 120 : 0;
		Integer maxDirect = high ? null : mid ? Integer.valueOf(180) : low ? Integer.valueOf(60) : null;

		List<int[]> places = NongsaroCodes.split(placementCodes).stream()
				.filter(NongsaroCodes.PLACEMENT::containsKey).map(NongsaroCodes.PLACEMENT::get).toList();
		Integer minDist = places.isEmpty() ? null : Integer.valueOf(places.stream().mapToInt(r -> r[0]).min().getAsInt());
		Integer maxDist = places.isEmpty() ? null : Integer.valueOf(places.stream().mapToInt(r -> r[1]).max().getAsInt());

		Integer winter = winterMinCode == null ? null : NongsaroCodes.WINTER_MIN.get(winterMinCode.trim());
		return new Converted(minLux, maxLux, minDirect, maxDirect, minDist, maxDist, winter);
	}
}
