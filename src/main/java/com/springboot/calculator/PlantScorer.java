package com.springboot.calculator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.springboot.domain.PlantSpecies;
import com.springboot.domain.SeasonDay;
import com.springboot.calculator.SunlightCalculator.Cell;

/**
 * 식물 적합도 점수(0~100). [① 분석 후 저장: 칸별 햇빛 + 식물 변환값 → 추천]
 * <ul>
 * <li>하늘빛 조도: 식물 lux 범위를 로그 눈금으로 4등분해 가운데 절반이면 1, 범위 끝으로 갈수록 0.8까지.
 * 범위 밖은 0.8에서 더 줄어든다 (모자라면 ×(lux/최소)^0.7, 넘치면 ×(최대/lux)^0.5)</li>
 * <li>직사광: 최소보다 적으면 비율만큼, 최대보다 많으면 (최대/직사광)^0.7</li>
 * <li>창에서 거리: 농사로 배치장소 범위에서 벗어난 거리만큼 감점 (150cm 이상 벗어나면 0.7)</li>
 * </ul>
 * 방 점수 = 가장 좋은 자리 점수 × 0.9 + 자리 여유(1년 내내 80점 이상인 칸의 비율) × 10.
 * 범위가 넓은 식물은 방 어디에나 100점짜리 칸이 있기 마련이라, 자리 점수만으로는 순위가 갈리지 않는다.
 * 1년 내내 70점 이상인 칸이 있으면 고정 자리, 없으면 계절마다 가장 좋은 칸(계절 이동 자리).
 */
public final class PlantScorer {

	public static final int FIXED_THRESHOLD = 70;
	/** 자리 여유로 보는 칸 점수 */
	public static final int ROOMY_THRESHOLD = 80;
	private static final double EDGE = 0.8;
	private static final double MAX_DISTANCE_PENALTY = 0.3;
	private static final double DISTANCE_PENALTY_SPAN_CM = 150;

	private PlantScorer() {
	}

	public static double score(PlantSpecies p, Cell c) {
		double light;
		if (c.lux() < p.getMinLux()) {
			light = EDGE * Math.pow(Math.max(c.lux(), 1) / p.getMinLux(), 0.7);
		} else if (c.lux() > p.getMaxLux()) {
			light = EDGE * Math.pow(p.getMaxLux() / c.lux(), 0.5);
		} else if (p.getMaxLux() > p.getMinLux()) {
			// 범위 안 위치(로그 눈금 0~1): 가운데 절반(0.25~0.75)이면 1, 끝(0 또는 1)이면 0.8
			double t = Math.log(c.lux() / p.getMinLux()) / Math.log((double) p.getMaxLux() / p.getMinLux());
			light = 1 - (1 - EDGE) * Math.max(0, Math.abs(t - 0.5) - 0.25) / 0.25;
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
		double distance = 1 - MAX_DISTANCE_PENALTY * Math.min(1, placementGap(p, c) / DISTANCE_PENALTY_SPAN_CM);
		return Math.round(100 * light * direct * distance * 10) / 10.0;
	}

	/** 농사로 배치장소(창에서 거리) 기준에 맞는가. 기준이 없으면 맞는 것으로 본다. */
	public static boolean withinPlacement(PlantSpecies p, Cell c) {
		return placementGap(p, c) == 0;
	}

	/** 농사로 배치장소 범위에서 벗어난 거리(cm). 범위 안이거나 기준이 없으면 0. */
	static double placementGap(PlantSpecies p, Cell c) {
		if (p.getMinWindowDistance() == null || p.getMaxWindowDistance() == null) {
			return 0;
		}
		return Math.max(0, Math.max(p.getMinWindowDistance() - c.distanceToWindow(),
				c.distanceToWindow() - p.getMaxWindowDistance()));
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
			List<String> notes, double roomyShare) {
	}

	/** 계절별 칸 결과(칸 순서가 계절마다 같아야 함)로 고정/계절 이동 자리를 찾는다. */
	public static PlantResult evaluate(PlantSpecies p, Map<SeasonDay, List<Cell>> seasons, Double roomWinterMinTemp) {
		List<Cell> base = seasons.get(SeasonDay.SPRING_EQUINOX);
		int n = base.size();
		// 고정 자리: 네 계절 최저 점수가 가장 높은 칸
		int bestIdx = -1;
		double bestMin = -1;
		double bestAvg = -1;
		int roomy = 0;
		for (int i = 0; i < n; i++) {
			double min = 100;
			double sum = 0;
			for (SeasonDay s : SeasonDay.values()) {
				double v = score(p, seasons.get(s).get(i));
				min = Math.min(min, v);
				sum += v;
			}
			double avg = sum / 4;
			if (min >= ROOMY_THRESHOLD) {
				roomy++;
			}
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

		double share = n == 0 ? 0 : (double) roomy / n;
		List<String> notes = new ArrayList<>();
		SpotType type;
		double score;
		if (fixed != null && fixed.score() >= FIXED_THRESHOLD) {
			type = SpotType.FIXED;
			score = bestMin * 0.6 + bestAvg * 0.4;
			if (!withinPlacement(p, fixed.cell())) {
				notes.add("농사로 추천 배치(창에서 " + p.getMinWindowDistance() + "~" + p.getMaxWindowDistance()
						+ "cm)와 다르지만 계산상 이 자리가 맞습니다.");
			}
		} else {
			type = SpotType.SEASONAL;
			score = seasonalSum / 4 * 0.9; // 옮겨야 하는 수고만큼 조금 낮춤
			notes.add("한 자리에서는 1년 내내 맞지 않아 계절마다 자리를 옮기는 것이 좋습니다.");
		}
		score = Math.round((score * 0.9 + share * 10) * 10) / 10.0;
		notes.add(String.format("1년 내내 잘 맞는(%d점 이상) 자리가 방의 %d%%입니다.", ROOMY_THRESHOLD, Math.round(share * 100)));
		if (p.getWinterMinTemp() != null && roomWinterMinTemp != null && roomWinterMinTemp < p.getWinterMinTemp()) {
			notes.add(String.format("겨울 실내 최저 %.1f℃가 이 식물의 겨울 최저온도 %d℃보다 낮습니다. 창가에서 떨어뜨리거나 보온하세요.",
					roomWinterMinTemp, p.getWinterMinTemp()));
			score = Math.round(score * 0.7 * 10) / 10.0;
		}
		return new PlantResult(p, type, score, fixed, seasonal, notes, share);
	}
}
