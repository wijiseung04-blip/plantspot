package com.plantspot.backend.obstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.backend.building.Building;
import com.plantspot.backend.building.BuildingRepository;
import com.plantspot.backend.house.House;
import com.plantspot.backend.house.HouseEvents;
import com.plantspot.backend.house.HouseService;
import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.obstruction.ObstructionCalculator.Obstacle;
import com.plantspot.backend.obstruction.ObstructionCalculator.Skyline;

/** 방향별 가림 각도 계산·저장. [① 분석 후 저장: 해 위치와 결합할 가림 각도] */
@Service
@Transactional(readOnly = true)
public class ObstructionService {

	private static final Logger log = LoggerFactory.getLogger(ObstructionService.class);

	/** 이 거리 안의 건물만 본다(m). 300m 밖 20층(60m)도 가림 각도 약 11°이지만 대부분 앞 건물에 가려진다. */
	public static final double SEARCH_RADIUS_M = 300;
	/** 층 바닥에서 눈(창 가운데) 높이(m) */
	public static final double EYE_ABOVE_FLOOR_M = 1.2;

	private final ObstructionAngleRepository repository;
	private final BuildingRepository buildings;
	private final HouseService houseService;
	private final ApplicationEventPublisher events;

	public ObstructionService(ObstructionAngleRepository repository, BuildingRepository buildings,
			HouseService houseService, ApplicationEventPublisher events) {
		this.repository = repository;
		this.buildings = buildings;
		this.houseService = houseService;
		this.events = events;
	}

	/** 층이 바뀌면 건물 데이터 값을 지워 다음 조회 때 다시 계산한다. */
	@EventListener
	public void onHouseChanged(HouseEvents.HouseChanged e) {
		repository.deleteByHouseAndSource(e.houseId(), ObstructionSource.BUILDING);
	}

	@EventListener
	public void onHouseDeleting(HouseEvents.HouseDeleting e) {
		repository.deleteByHouse(e.houseId());
	}

	/** 집 위치·층에서 본 건물 데이터 스카이라인 (저장하지 않음, 비회원 간단 예측에도 사용). */
	public Skyline computeFromBuildings(double x, double y, double eyeHeight, Long excludeBuildingId) {
		List<Obstacle> obstacles = buildings.findInBox(x - SEARCH_RADIUS_M, x + SEARCH_RADIUS_M,
				y - SEARCH_RADIUS_M, y + SEARCH_RADIUS_M).stream()
				.filter(b -> !Objects.equals(b.getId(), excludeBuildingId))
				.map(b -> new Obstacle(b.getId(), b.getHeight(), b.getCenterX(), b.getCenterY(), b.getFootprintWkt()))
				.toList();
		return ObstructionCalculator.calculate(x, y, eyeHeight, obstacles);
	}

	public static double eyeHeight(House house) {
		return house.floorElevation() + EYE_ABOVE_FLOOR_M;
	}

	@Transactional
	public List<ObstructionView> recalculate(LoginMember me, Long houseId) {
		House house = houseService.loadHouse(me, houseId);
		recalculate(house);
		return view(house);
	}

	@Transactional
	public void recalculate(House house) {
		Building own = house.getBuilding();
		Skyline sky = computeFromBuildings(house.getCenterX(), house.getCenterY(), eyeHeight(house),
				own != null ? own.getId() : null);
		repository.deleteByHouseAndSource(house.getId(), ObstructionSource.BUILDING);
		List<ObstructionAngle> rows = new ArrayList<>();
		for (int i = 0; i < ObstructionCalculator.BINS; i++) {
			rows.add(new ObstructionAngle(house, i * ObstructionCalculator.BIN_DEG, sky.angles()[i],
					ObstructionSource.BUILDING, sky.causeIds()[i] >= 0 ? sky.causeIds()[i] : null,
					sky.causeIds()[i] >= 0 ? sky.distances()[i] : null));
		}
		repository.saveAll(rows);
		events.publishEvent(new ObstructionChanged(house.getId()));
		log.info("가림 각도 계산: 집 {} (눈 높이 {}m)", house.getId(), eyeHeight(house));
	}

	/** 계산에 쓸 프로필. 건물 데이터 값이 아직 없으면 계산해서 저장한다. */
	@Transactional
	public ObstructionProfile profile(House house) {
		List<ObstructionAngle> rows = repository.findByHouseIdOrderByAzimuthBinAsc(house.getId());
		if (rows.stream().noneMatch(r -> r.getSource() == ObstructionSource.BUILDING)) {
			recalculate(house);
			rows = repository.findByHouseIdOrderByAzimuthBinAsc(house.getId());
		}
		ObstructionProfile p = ObstructionProfile.empty();
		for (ObstructionAngle r : rows) {
			int bin = r.getAzimuthBin() / ObstructionCalculator.BIN_DEG;
			if (r.getSource() == ObstructionSource.BUILDING) {
				p.building()[bin] = r.getAngleDeg();
			} else {
				p.sensor()[bin] = r.getAngleDeg();
			}
		}
		return p;
	}

	@Transactional
	public List<ObstructionView> view(LoginMember me, Long houseId) {
		return view(houseService.loadHouse(me, houseId));
	}

	private List<ObstructionView> view(House house) {
		ObstructionProfile p = profile(house);
		List<ObstructionAngle> rows = repository.findByHouseIdOrderByAzimuthBinAsc(house.getId());
		List<ObstructionView> result = new ArrayList<>();
		for (int i = 0; i < ObstructionCalculator.BINS; i++) {
			int bin = i * ObstructionCalculator.BIN_DEG;
			ObstructionAngle b = rows.stream()
					.filter(r -> r.getAzimuthBin() == bin && r.getSource() == ObstructionSource.BUILDING)
					.findFirst().orElse(null);
			double sensor = p.sensor()[i];
			result.add(new ObstructionView(bin, round(p.building()[i]), Double.isNaN(sensor) ? null : round(sensor),
					round(p.angleAt(bin)), Double.isNaN(sensor) ? ObstructionSource.BUILDING : ObstructionSource.SENSOR,
					b != null ? b.getBuildingId() : null,
					b != null && b.getDistance() != null ? round(b.getDistance()) : null));
		}
		return result;
	}

	/**
	 * 실측 가림 각도 저장 (센서 분석 결과 또는 직접 측정). 들어온 방향 칸만 SENSOR 값으로 바꾼다.
	 * 4단계 센서 보정 프로그램이 이 메서드를 쓴다.
	 */
	@Transactional
	public List<ObstructionView> saveMeasured(LoginMember me, Long houseId, List<MeasuredAngle> measured) {
		House house = houseService.loadHouse(me, houseId);
		List<ObstructionAngle> existing = repository.findByHouseIdOrderByAzimuthBinAsc(houseId);
		for (MeasuredAngle m : measured) {
			int bin = ObstructionCalculator.binOf(m.azimuthDeg()) * ObstructionCalculator.BIN_DEG;
			existing.stream().filter(r -> r.getAzimuthBin() == bin && r.getSource() == ObstructionSource.SENSOR)
					.forEach(repository::delete);
			repository.flush();
			repository.save(new ObstructionAngle(house, bin, m.angleDeg(), ObstructionSource.SENSOR, null, null));
		}
		events.publishEvent(new ObstructionChanged(houseId));
		log.info("실측 가림 각도 저장: 집 {} {}개 방향", houseId, measured.size());
		return view(house);
	}

	@Transactional
	public List<ObstructionView> clearMeasured(LoginMember me, Long houseId) {
		House house = houseService.loadHouse(me, houseId);
		repository.deleteByHouseAndSource(houseId, ObstructionSource.SENSOR);
		events.publishEvent(new ObstructionChanged(houseId));
		return view(house);
	}

	/** 가림 각도가 바뀜 → 그 집 방들의 햇빛 계산이 낡음 */
	public record ObstructionChanged(Long houseId) {
	}

	private static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}

	/** 방향 칸 하나: 건물 데이터 값, 실측 값(없으면 null), 실제 쓰는 값. */
	public record ObstructionView(int azimuth, double buildingAngle, Double sensorAngle, double angle,
			ObstructionSource source, Long buildingId, Double distance) {
	}

	public record MeasuredAngle(
			@jakarta.validation.constraints.DecimalMin(value = "0", message = "방향은 0~360도입니다.")
			@jakarta.validation.constraints.DecimalMax(value = "360", message = "방향은 0~360도입니다.") double azimuthDeg,
			@jakarta.validation.constraints.DecimalMin(value = "0", message = "가림 각도는 0~90도입니다.")
			@jakarta.validation.constraints.DecimalMax(value = "90", message = "가림 각도는 0~90도입니다.") double angleDeg) {
	}
}
