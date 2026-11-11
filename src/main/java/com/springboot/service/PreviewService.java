package com.springboot.service;

import com.springboot.calculator.PlantScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.Building;
import com.springboot.domain.Point;
import com.springboot.calculator.Shape;
import com.springboot.calculator.BuildingFacade;
import com.springboot.calculator.FloorRule;
import com.springboot.calculator.ObstructionProfile;
import com.springboot.calculator.SolarCalculator;
import com.springboot.domain.LightLevel;
import com.springboot.domain.SeasonDay;
import com.springboot.calculator.SunlightCalculator;
import com.springboot.calculator.SunlightCalculator.Cell;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 비회원 간단 예측: 동·지번, 층, 창 방향만으로 표준 방(4m × 3.5m, 창 180 × 150cm, 창턱 60cm)을 가정해
 * 계절별 창가 햇빛과 어울리는 식물을 보여준다. 저장하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class PreviewService {

	private static final List<Point> ROOM = List.of(new Point(0, 0), new Point(400, 0), new Point(400, 350),
			new Point(0, 350));

	private final BuildingService buildingService;
	private final ObstructionService obstructionService;
	private final PlantService plantService;
	private final SunlightCalculator calculator;

	public PreviewService(BuildingService buildingService, ObstructionService obstructionService,
			PlantService plantService, SolarCalculator solar) {
		this.buildingService = buildingService;
		this.obstructionService = obstructionService;
		this.plantService = plantService;
		this.calculator = new SunlightCalculator(solar);
	}

	public record PreviewRequest(
			@NotBlank(message = "동을 선택하세요.") String dong,
			@NotBlank(message = "지번을 입력하세요.") String jibun,
			@NotNull(message = "층을 입력하세요.") @Min(value = 1, message = "층은 1 이상입니다.")
			@Max(value = 80, message = "층은 80 이하입니다.") Integer floor,
			@NotNull(message = "창 방향을 선택하세요.") @DecimalMin(value = "0", message = "창 방향은 0~360도입니다.")
			@DecimalMax(value = "360", message = "창 방향은 0~360도입니다.") Double windowAzimuth,
			/** 같은 지번에 건물이 여러 동일 때 고른 건물 (없으면 가장 높은 건물) */
			Long buildingId,
			/** 동 안 세대 위치 −1 ~ 1 (없으면 가운데) */
			@DecimalMin(value = "-1", message = "세대 위치는 -1~1입니다.")
			@DecimalMax(value = "1", message = "세대 위치는 -1~1입니다.") Double unitPosition) {
	}

	public PreviewResult preview(PreviewRequest req) {
		Building b = buildingService.choose(req.dong(), req.jibun(), req.buildingId());
		HouseService.checkFloor(b, req.floor());
		double eye = (req.floor() - 1) * 3.0 + ObstructionService.EYE_ABOVE_FLOOR_M;
		double azimuth = Shape.normalize(req.windowAzimuth());
		// 창 관측점: 우리 건물 외벽(창 방향) 바로 앞
		BuildingFacade.Observer o = BuildingFacade.observer(b.getFootprintWkt(), b.getCenterX(), b.getCenterY(),
				azimuth, req.unitPosition() != null ? req.unitPosition() : 0);
		ObstructionProfile profile = obstructionService.publicProfileAt(b, o, eye);
		// 2번 벽(아래쪽)이 창 벽: 평면도를 돌려 그 벽이 창 방향을 보게 한다
		double orientation = Shape.normalize(azimuth - 180);
		var room = SunlightCalculator.room(ROOM, orientation, 230,
				List.<double[]>of(new double[] { 0, 2, 110, 180, 150, 60, azimuth }), 1.0);

		int year = SunlightService.currentYear();
		Map<SeasonDay, List<Cell>> seasons = new EnumMap<>(SeasonDay.class);
		List<SeasonPreview> seasonList = new ArrayList<>();
		for (SeasonDay s : SeasonDay.values()) {
			var r = calculator.calculate(room, b.getLatitude(), b.getLongitude(), s.date(year), profile,
					SunlightCalculator.Options.defaults());
			seasons.put(s, r.cells());
			seasonList.add(new SeasonPreview(s, s.getLabel(), r.windowSunMinutes().get(0), zone(r.cells(), 0, 100),
					zone(r.cells(), 100, 250), zone(r.cells(), 250, 10_000)));
		}

		List<PreviewPlant> plants = plantService.all().stream()
				.map(p -> PlantScorer.evaluate(p, seasons, null))
				.sorted(Comparator.comparingDouble(PlantScorer.PlantResult::score).reversed())
				.limit(6)
				.map(r -> new PreviewPlant(r.plant().getId(), r.plant().getName(), r.plant().getThumbnailUrl(),
						r.type(), r.score(),
						r.type() == PlantScorer.SpotType.FIXED ? Math.round(r.fixed().cell().distanceToWindow()) : null))
				.toList();

		List<Double> skyline = new ArrayList<>();
		for (double a : profile.building()) {
			skyline.add(Math.round(a * 10) / 10.0);
		}
		double front = profile.averageAngle(azimuth, 45);
		return new PreviewResult(b.getDong(), b.getJibun(), b.getHeight(), req.floor(), azimuth,
				Shape.directionName(azimuth), Math.round(front * 10) / 10.0, skyline, seasonList, plants,
				plantService.count(), FloorRule.maxFloor(b), FloorRule.warning(b));
	}

	/** 창에서 거리 구간의 평균 직사광·조도와 밝기 단계. */
	private static Zone zone(List<Cell> cells, double from, double to) {
		List<Cell> in = cells.stream().filter(c -> c.distanceToWindow() >= from && c.distanceToWindow() < to).toList();
		if (in.isEmpty()) {
			return new Zone(0, 0, LightLevel.DIM);
		}
		int direct = (int) Math.round(in.stream().mapToInt(Cell::directMinutes).average().orElse(0));
		double lux = Math.round(in.stream().mapToDouble(Cell::lux).average().orElse(0));
		return new Zone(direct, lux, LightLevel.of(lux, direct));
	}

	public record Zone(int directMinutes, double lux, LightLevel level) {
	}

	public record SeasonPreview(SeasonDay season, String label, int windowSunMinutes, Zone nearWindow, Zone middle,
			Zone deep) {
	}

	public record PreviewPlant(Long id, String name, String thumbnailUrl, PlantScorer.SpotType type, double score,
			Long distanceFromWindow) {
	}

	public record PreviewResult(String dong, String jibun, double buildingHeight, int floor, double windowAzimuth,
			String direction, double frontObstructionDeg, List<Double> skyline, List<SeasonPreview> seasons,
			List<PreviewPlant> plants, long plantCount,
			/** 건물에서 고를 수 있는 가장 높은 층 (모르면 null) */ Integer maxFloor,
			/** 층수를 확인하지 못했을 때 안내 */ String floorWarning) {
	}
}
