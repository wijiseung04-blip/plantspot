package com.plantspot.recommend;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.plantspot.plant.PlantSpecies;
import com.plantspot.sunlight.SeasonDay;
import com.plantspot.sunlight.SunlightCalculator.Cell;

/**
 * 식물 적합도 점수(0~100). [① 분석 후 저장: 칸별 햇빛 + 식물 변환값 → 추천]
 * <ul>
 * <li>하늘빛 조도: 식물 lux 범위 안이면 1, 모자라면 (lux/최소)^0.7, 넘치면 (최대/lux)^0.5</li>
 * <li>직사광: 최소보다 적으면 비율만큼, 최대보다 많으면 (최대/직사광)^0.7</li>
 * <li>창에서 거리: 농사로 배치장소 범위 밖이면 0.85 (공공데이터 기준은 참고용이라 약하게)</li>
 * </ul>
 * 1년 내내 70점 이상인 칸이 있으면 고정 자리, 없으면 계절마다 가장 좋은 칸(계절 이동 자리).
 */
public final class PlantScorer {

	public static final int FIXED_THRESHOLD = 70;
	private static final double DISTANCE_PENALTY = 0.85;

	private PlantScorer() {
	}

	public static double score(PlantSpecies p, Cell c) {
		double light;
		if (c.lux() < p.getMinLux()) {
			light = Math.pow(Math.max(c.lux(), 1) / p.getMinLux(), 0.7);
		} else if (c.lux() > p.getMaxLux()) {
			light = Math.pow(p.getMaxLux() / c.lux(), 0.5);
		} else {
			light = 1;
		}
		double direct = 1;
		if (p.getMinDirectMinutes() > 0 && c.directMinutes() < p.getMinDirectMinutes()) {
			direct = 0.3 + 0.7 * c.directMinutes() / p.getMinDirectMinutes();
		}
		if (p.getMaxDirectMinutes() != null && c.directMinutes() > p.getMaxDirectMinutes()) {
			direct *= Math.pow((double) Math.max(p.getMaxDirectMinutes(), 15) / c.directMinutes(), 0.7);
		}
		double distance = withinPlacement(p, c) ? 1 : DISTANCE_PENALTY;
		return Math.round(100 * light * direct * distance * 10) / 10.0;
	}

	/** 농사로 배치장소(창에서 거리) 기준에 맞는가. 기준이 없으면 맞는 것으로 본다. */
	public static boolean withinPlacement(PlantSpecies p, Cell c) {
		if (p.getMinWindowDistance() == null || p.getMaxWindowDistance() == null) {
			return true;
		}
		return c.distanceToWindow() >= p.getMinWindowDistance() && c.distanceToWindow() <= p.getMaxWindowDistance();
	}

	public enum SpotType {
		/** 1년 내내 같은 자리 */
		FIXED,
		/** 계절마다 옮기는 자리 */
		SEASONAL
	}

	public record Spot(SeasonDay season, Cell cell, double score) {
	}

	/** 한 식물의 방 추천 결과. */
	public record PlantResult(PlantSpecies plant, SpotType type, double score, Spot fixed, Map<SeasonDay, Spot> seasonal,
			List<String> notes) {
	}

	/** 계절별 칸 결과(칸 순서가 계절마다 같아야 함)로 고정/계절 이동 자리를 찾는다. */
	public static PlantResult evaluate(PlantSpecies p, Map<SeasonDay, List<Cell>> seasons, Double roomWinterMinTemp) {
		List<Cell> base = seasons.get(SeasonDay.SPRING_EQUINOX);
		int n = base.size();
		// 고정 자리: 네 계절 최저 점수가 가장 높은 칸
		int bestIdx = -1;
		double bestMin = -1;
		double bestAvg = -1;
		for (int i = 0; i < n; i++) {
			double min = 100;
			double sum = 0;
			for (SeasonDay s : SeasonDay.values()) {
				double v = score(p, seasons.get(s).get(i));
				min = Math.min(min, v);
				sum += v;
			}
			double avg = sum / 4;
			if (min > bestMin || (min == bestMin && avg > bestAvg)) {
				bestMin = min;
				bestAvg = avg;
				bestIdx = i;
			}
		}
		Map<SeasonDay, Spot> seasonal = new EnumMap<>(SeasonDay.class);
		double seasonalSum = 0;
		for (SeasonDay s : SeasonDay.values()) {
			List<Cell> cells = seasons.get(s);
			Spot best = null;
			for (Cell c : cells) {
				double v = score(p, c);
				if (best == null || v > best.score()) {
					best = new Spot(s, c, v);
				}
			}
			seasonal.put(s, best);
			seasonalSum += best == null ? 0 : best.score();
		}
		Spot fixed = bestIdx < 0 ? null
				: new Spot(null, base.get(bestIdx), bestMin);

		List<String> notes = new ArrayList<>();
		SpotType type;
		double score;
		if (fixed != null && fixed.score() >= FIXED_THRESHOLD) {
			type = SpotType.FIXED;
			score = Math.round((bestMin * 0.6 + bestAvg * 0.4) * 10) / 10.0;
			if (!withinPlacement(p, fixed.cell())) {
				notes.add("농사로 추천 배치(창에서 " + p.getMinWindowDistance() + "~" + p.getMaxWindowDistance()
						+ "cm)와 다르지만 계산상 이 자리가 맞습니다.");
			}
		} else {
			type = SpotType.SEASONAL;
			score = Math.round(seasonalSum / 4 * 0.9 * 10) / 10.0; // 옮겨야 하는 수고만큼 조금 낮춤
			notes.add("한 자리에서는 1년 내내 맞지 않아 계절마다 자리를 옮기는 것이 좋습니다.");
		}
		if (p.getWinterMinTemp() != null && roomWinterMinTemp != null && roomWinterMinTemp < p.getWinterMinTemp()) {
			notes.add(String.format("겨울 실내 최저 %.1f℃가 이 식물의 겨울 최저온도 %d℃보다 낮습니다. 창가에서 떨어뜨리거나 보온하세요.",
					roomWinterMinTemp, p.getWinterMinTemp()));
			score = Math.round(score * 0.7 * 10) / 10.0;
		}
		return new PlantResult(p, type, score, fixed, seasonal, notes);
	}
}
