package com.springboot.service;

import com.springboot.calculator.ObstructionCalculator;
import com.springboot.calculator.ObstructionProfile;
import com.springboot.calculator.BuildingFacade;
import com.springboot.calculator.TerrainCalculator;
import com.springboot.calculator.TerrainModel;
import com.springboot.domain.ObstructionAngle;
import com.springboot.domain.ObstructionSource;
import com.springboot.repository.ObstructionAngleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.Building;
import com.springboot.repository.BuildingRepository;
import com.springboot.domain.House;
import com.springboot.event.HouseEvents;
import com.springboot.security.LoginMember;
import com.springboot.calculator.ObstructionCalculator.Obstacle;
import com.springboot.calculator.ObstructionCalculator.Skyline;

/**
 * 방향별 가림 각도 계산·저장. [① 분석 후 저장: 해 위치와 결합할 가림 각도]
 * 공공데이터 값 = max(건물, 지형). 건물은 300m 안, 땅 높이 차(경사지)를 더해 계산하고, 지형(산)은 300m~10km.
 * 지형 자료(importTerrain)가 없으면 평지로 보고 건물만 계산한다.
 */
@Service
@Transactional(readOnly = true)
public class ObstructionService {

	private static final Logger log = LoggerFactory.getLogger(ObstructionService.class);

	/** 이 거리 안의 건물만 본다(m). 300m 밖 20층(60m)도 가림 각도 약 11°이지만 대부분 앞 건물에 가려진다. */
	public static final double SEARCH_RADIUS_M = 300;
	/** 층 바닥에서 눈(창 가운데) 높이(m) */
	public static final double EYE_ABOVE_FLOOR_M = 1.2;
	/** 산 스카이라인을 보는 최대 거리(m) */
	public static final double TERRAIN_MAX_M = 10_000;

	private final ObstructionAngleRepository repository;
	private final BuildingRepository buildings;
	private final HouseService houseService;
	private final ApplicationEventPublisher events;
	private final TerrainService terrain;
	/** 창별 공공데이터 가림 각도 (집·방향·눈 높이·세대 위치별, 집이 바뀌면 비운다) */
	private final java.util.Map<String, PublicSky> windowCache = new java.util.concurrent.ConcurrentHashMap<>();

	/** 방향 칸 72개의 공공데이터 가림 각도와 원인 (OWN 우리 건물 / BUILDING 옆 건물 / TERRAIN 지형 / NONE). */
	public record PublicSky(double[] angles, String[] causes, long[] buildingIds, double[] distances) {
	}

	public ObstructionService(ObstructionAngleRepository repository, BuildingRepository buildings,
			HouseService houseService, ApplicationEventPublisher events, TerrainService terrain) {
		this.repository = repository;
		this.buildings = buildings;
		this.houseService = houseService;
		this.events = events;
		this.terrain = terrain;
	}

	/** 층이 바뀌면 건물 데이터 값을 지워 다음 조회 때 다시 계산한다. */
	@EventListener
	public void onHouseChanged(HouseEvents.HouseChanged e) {
		repository.deleteByHouseAndSource(e.houseId(), ObstructionSource.BUILDING);
		repository.deleteByHouseAndSource(e.houseId(), ObstructionSource.TERRAIN);
	}

	@EventListener
	public void onHouseChangedClearCache(HouseEvents.HouseChanged e) {
		clearCache(e.houseId());
	}

	@EventListener
	public void onObstructionChanged(ObstructionChanged e) {
		clearCache(e.houseId());
	}

	private void clearCache(Long houseId) {
		windowCache.keySet().removeIf(k -> k.startsWith(houseId + ":"));
	}

	@EventListener
	public void onHouseDeleting(HouseEvents.HouseDeleting e) {
		repository.deleteByHouse(e.houseId());
	}

	/**
	 * 집 위치·층에서 본 건물 데이터 스카이라인 (저장하지 않음).
	 * eyeHeight 는 우리 땅에서 잰 높이. 지형 자료가 있으면 건물마다 (그 건물 땅 높이 − 우리 땅 높이)를 높이에 더한다.
	 */
	public Skyline computeFromBuildings(double x, double y, double eyeHeight, Long excludeBuildingId) {
		double ground = terrain.groundAt(x, y);
		List<Obstacle> obstacles = buildings.findInBox(x - SEARCH_RADIUS_M, x + SEARCH_RADIUS_M,
				y - SEARCH_RADIUS_M, y + SEARCH_RADIUS_M).stream()
				.filter(b -> !Objects.equals(b.getId(), excludeBuildingId))
				.map(b -> new Obstacle(b.getId(), b.getHeight() + groundDiff(ground, b.getCenterX(), b.getCenterY()),
						b.getCenterX(), b.getCenterY(), b.getFootprintWkt()))
				.toList();
		return ObstructionCalculator.calculate(x, y, eyeHeight, obstacles);
	}

	private double groundDiff(double ground, double x, double y) {
		if (Double.isNaN(ground)) {
			return 0;
		}
		double g = terrain.groundAt(x, y);
		return Double.isNaN(g) ? 0 : g - ground;
	}

	/** 땅 높낮이(산) 스카이라인. 지형 자료가 없으면 null. */
	public TerrainCalculator.TerrainSkyline computeFromTerrain(double x, double y, double eyeHeight) {
		TerrainModel model = terrain.model();
		double ground = model == null ? Double.NaN : model.elevationAt(x, y);
		if (Double.isNaN(ground)) {
			return null;
		}
		return TerrainCalculator.calculate(model, x, y, ground + eyeHeight, SEARCH_RADIUS_M, TERRAIN_MAX_M);
	}

	/** 창 방향 azimuth 인 창의 관측점 (우리 건물 외벽 바로 앞, 동 안 세대 위치 반영). */
	public BuildingFacade.Observer observer(House house, double azimuth) {
		Building own = house.getBuilding();
		return BuildingFacade.observer(own != null ? own.getFootprintWkt() : null, house.getCenterX(),
				house.getCenterY(), azimuth, house.unitPosition());
	}

	/**
	 * 창 하나의 가림 각도: 그 창 관측점에서 본 건물·지형 + 집에 저장된 센서 실측.
	 * (집 화면 스카이라인은 건물 가운데 기준, 햇빛 계산은 이 창별 값을 쓴다)
	 */
	@Transactional
	public ObstructionProfile windowProfile(House house, double azimuth) {
		ObstructionProfile base = profile(house);
		double eye = eyeHeight(house);
		String key = house.getId() + ":" + Math.round(azimuth * 10) + ":" + eye + ":" + house.unitPosition() + ":"
				+ (terrain.model() != null);
		return new ObstructionProfile(windowSky(house, azimuth).angles().clone(), base.sensor());
	}

	/** 창 관측점에서 본 공공데이터 가림 각도와 원인 (메모리 캐시, 집이 바뀌면 비움). */
	private PublicSky windowSky(House house, double azimuth) {
		double eye = eyeHeight(house);
		String key = house.getId() + ":" + Math.round(azimuth * 10) + ":" + eye + ":" + house.unitPosition() + ":"
				+ (terrain.model() != null);
		return windowCache.computeIfAbsent(key, k -> publicSkyAt(house.getBuilding(), observer(house, azimuth), eye));
	}

	/** 방의 창마다 관측점·창 앞 가림 각도 (방 화면). */
	@Transactional
	public List<WindowObstruction> windowViews(LoginMember me, Long roomId) {
		com.springboot.domain.Room room = houseService.loadRoom(me, roomId);
		House house = room.getHouse();
		List<WindowObstruction> list = new ArrayList<>();
		for (com.springboot.domain.RoomWindow w : room.getWindows()) {
			double az = w.getAzimuthDeg();
			ObstructionProfile p = windowProfile(house, az);
			double max = 0;
			int maxAz = (int) Math.round(az);
			for (int d = -85; d <= 85; d += ObstructionCalculator.BIN_DEG) {
				double a = p.angleAt(az + d);
				if (a > max) {
					max = a;
					maxAz = (int) Math.round(((az + d) % 360 + 360) % 360);
				}
			}
			PublicSky sky = windowSky(house, az);
			List<Double> angles = new ArrayList<>();
			List<SkyBin> bins = new ArrayList<>();
			for (int i = 0; i < ObstructionCalculator.BINS; i++) {
				double a = p.angleAt(i * ObstructionCalculator.BIN_DEG);
				angles.add(round(a));
				boolean sensor = !Double.isNaN(p.sensor()[i]);
				String cause = sensor ? "SENSOR" : sky.causes()[i];
				Long bid = !sensor && sky.buildingIds()[i] >= 0 ? sky.buildingIds()[i] : null;
				Double dist = !sensor && sky.distances()[i] > 0 ? round(sky.distances()[i]) : null;
				bins.add(new SkyBin(i * ObstructionCalculator.BIN_DEG, round(a), cause, bid, null, dist));
			}
			list.add(new WindowObstruction(w.getId(), az, round(observer(house, az).offsetFromCenter()),
					round(p.averageAngle(az, 45)), round(max), maxAz, angles, bins));
		}
		return withLabels(list, house.getBuilding());
	}

	/** 원인 건물 번호에 이름(동 이름·건물명·지번)을 붙인다. */
	private List<WindowObstruction> withLabels(List<WindowObstruction> list, Building own) {
		java.util.Set<Long> ids = new java.util.HashSet<>();
		list.forEach(w -> w.bins().forEach(b -> {
			if (b.buildingId() != null) {
				ids.add(b.buildingId());
			}
		}));
		java.util.Map<Long, String> names = new java.util.HashMap<>();
		buildings.findAllById(ids).forEach(b -> names.put(b.getId(), buildingName(b)));
		return list.stream().map(w -> new WindowObstruction(w.windowId(), w.azimuth(), w.observerOffset(),
				w.frontAverage(), w.frontMax(), w.frontMaxAzimuth(), w.angles(),
				w.bins().stream().map(b -> new SkyBin(b.azimuth(), b.angle(), b.cause(), b.buildingId(),
						label(b, names, own), b.distance())).toList()))
				.toList();
	}

	private static String label(SkyBin b, java.util.Map<Long, String> names, Building own) {
		return switch (b.cause()) {
		case "OWN" -> "우리 건물" + (own != null && names.containsKey(own.getId()) ? " (" + names.get(own.getId()) + ")" : "");
		case "BUILDING" -> b.buildingId() != null ? names.getOrDefault(b.buildingId(), "옆 건물") : "옆 건물";
		case "TERRAIN" -> "산·지형";
		case "SENSOR" -> "센서 실측";
		default -> null;
		};
	}

	private static String buildingName(Building b) {
		String name = b.getDongLabel() != null && !b.getDongLabel().isBlank() ? b.getDongLabel()
				: b.getBuildingName() != null && !b.getBuildingName().isBlank() ? b.getBuildingName() : null;
		return (name != null ? name + " · " : "") + b.getDong() + " " + b.getJibun();
	}

	/** 방향 칸 하나: 실제 쓰는 가림 각도와 주된 원인 (가장 높게 가린 것 하나). */
	public record SkyBin(int azimuth, double angle, String cause, Long buildingId, String label, Double distance) {
	}

	/** 창 하나: 관측점이 건물 중심에서 떨어진 거리(m), 창 앞 ±45° 평균, 창 앞 ±85° 안 최대 가림 각도와 그 방향. */
	public record WindowObstruction(Long windowId, double azimuth, double observerOffset, double frontAverage,
			double frontMax, int frontMaxAzimuth, List<Double> angles, List<SkyBin> bins) {
	}

	/**
	 * 창 관측점(우리 건물 외벽 앞)에서 본 공공데이터 프로필. 관측점이 우리 건물 바깥이면 우리 건물도 가림물로 넣는다
	 * (ㄷ자 건물의 반대쪽 날개 등), 아니면 뺀다. 창별 햇빛·비회원 예측에 쓴다.
	 */
	public ObstructionProfile publicProfileAt(Building own, BuildingFacade.Observer o, double eyeHeight) {
		return new ObstructionProfile(publicSkyAt(own, o, eyeHeight).angles(), ObstructionProfile.empty().sensor());
	}

	/** publicProfileAt 과 같은 계산 + 방향별 원인. */
	public PublicSky publicSkyAt(Building own, BuildingFacade.Observer o, double eyeHeight) {
		boolean includeOwn = own != null && BuildingFacade.canSeeOwnBuilding(own.getFootprintWkt(), o);
		Skyline sky = computeFromBuildings(o.x(), o.y(), eyeHeight, own == null || includeOwn ? null : own.getId());
		TerrainCalculator.TerrainSkyline land = computeFromTerrain(o.x(), o.y(), eyeHeight);
		int n = ObstructionCalculator.BINS;
		double[] angles = new double[n];
		String[] causes = new String[n];
		long[] ids = new long[n];
		double[] dist = new double[n];
		for (int i = 0; i < n; i++) {
			double b = sky.angles()[i];
			double t = land != null ? land.angles()[i] : 0;
			if (t > b) {
				angles[i] = t;
				causes[i] = "TERRAIN";
				ids[i] = -1;
				dist[i] = land.distances()[i];
			} else if (b > 0) {
				angles[i] = b;
				ids[i] = sky.causeIds()[i];
				causes[i] = own != null && ids[i] == own.getId() ? "OWN" : "BUILDING";
				dist[i] = sky.distances()[i];
			} else {
				causes[i] = "NONE";
				ids[i] = -1;
			}
		}
		return new PublicSky(angles, causes, ids, dist);
	}

	/** 공공데이터(건물 + 지형) 프로필 — 비회원 간단 예측·창별 계산용 (저장하지 않음). */
	public ObstructionProfile publicProfile(double x, double y, double eyeHeight, Long excludeBuildingId) {
		Skyline sky = computeFromBuildings(x, y, eyeHeight, excludeBuildingId);
		TerrainCalculator.TerrainSkyline land = computeFromTerrain(x, y, eyeHeight);
		double[] angles = sky.angles().clone();
		if (land != null) {
			for (int i = 0; i < angles.length; i++) {
				angles[i] = Math.max(angles[i], land.angles()[i]);
			}
		}
		return new ObstructionProfile(angles, ObstructionProfile.empty().sensor());
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
		TerrainCalculator.TerrainSkyline land = computeFromTerrain(house.getCenterX(), house.getCenterY(),
				eyeHeight(house));
		repository.deleteByHouseAndSource(house.getId(), ObstructionSource.BUILDING);
		repository.deleteByHouseAndSource(house.getId(), ObstructionSource.TERRAIN);
		List<ObstructionAngle> rows = new ArrayList<>();
		for (int i = 0; i < ObstructionCalculator.BINS; i++) {
			rows.add(new ObstructionAngle(house, i * ObstructionCalculator.BIN_DEG, sky.angles()[i],
					ObstructionSource.BUILDING, sky.causeIds()[i] >= 0 ? sky.causeIds()[i] : null,
					sky.causeIds()[i] >= 0 ? sky.distances()[i] : null));
			if (land != null) {
				rows.add(new ObstructionAngle(house, i * ObstructionCalculator.BIN_DEG, land.angles()[i],
						ObstructionSource.TERRAIN, null, land.angles()[i] > 0 ? land.distances()[i] : null));
			}
		}
		repository.saveAll(rows);
		events.publishEvent(new ObstructionChanged(house.getId()));
		log.info("가림 각도 계산: 집 {} (눈 높이 {}m, 지형 {})", house.getId(), eyeHeight(house),
				land != null ? "반영" : "자료 없음");
	}

	/** 계산에 쓸 프로필. 건물 데이터 값이 아직 없으면 계산해서 저장한다. */
	@Transactional
	public ObstructionProfile profile(House house) {
		List<ObstructionAngle> rows = repository.findByHouseIdOrderByAzimuthBinAsc(house.getId());
		boolean stale = rows.stream().noneMatch(r -> r.getSource() == ObstructionSource.BUILDING)
				// 지형 자료를 나중에 가져왔으면 다시 계산
				|| (terrain.model() != null && rows.stream().noneMatch(r -> r.getSource() == ObstructionSource.TERRAIN));
		if (stale) {
			recalculate(house);
			rows = repository.findByHouseIdOrderByAzimuthBinAsc(house.getId());
		}
		ObstructionProfile p = ObstructionProfile.empty();
		for (ObstructionAngle r : rows) {
			int bin = r.getAzimuthBin() / ObstructionCalculator.BIN_DEG;
			if (r.getSource() == ObstructionSource.BUILDING || r.getSource() == ObstructionSource.TERRAIN) {
				p.building()[bin] = Math.max(p.building()[bin], r.getAngleDeg());
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
			ObstructionAngle b = find(rows, bin, ObstructionSource.BUILDING);
			ObstructionAngle t = find(rows, bin, ObstructionSource.TERRAIN);
			double sensor = p.sensor()[i];
			boolean byTerrain = t != null && (b == null || t.getAngleDeg() > b.getAngleDeg());
			ObstructionAngle cause = byTerrain ? t : b;
			ObstructionSource source = !Double.isNaN(sensor) ? ObstructionSource.SENSOR
					: byTerrain ? ObstructionSource.TERRAIN : ObstructionSource.BUILDING;
			result.add(new ObstructionView(bin, round(p.building()[i]), Double.isNaN(sensor) ? null : round(sensor),
					round(p.angleAt(bin)), source, cause != null ? cause.getBuildingId() : null,
					cause != null && cause.getDistance() != null ? round(cause.getDistance()) : null,
					b != null ? round(b.getAngleDeg()) : null, t != null ? round(t.getAngleDeg()) : null));
		}
		return result;
	}

	private static ObstructionAngle find(List<ObstructionAngle> rows, int bin, ObstructionSource source) {
		return rows.stream().filter(r -> r.getAzimuthBin() == bin && r.getSource() == source).findFirst().orElse(null);
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

	/**
	 * 방향 칸 하나. buildingAngle = 공공데이터 값(건물·지형 중 큰 값, 보정 전), sensorAngle = 실측(없으면 null),
	 * angle = 실제 쓰는 값, source = angle 의 출처, onlyBuildingAngle·terrainAngle = 나눠 본 값.
	 */
	public record ObstructionView(int azimuth, double buildingAngle, Double sensorAngle, double angle,
			ObstructionSource source, Long buildingId, Double distance, Double onlyBuildingAngle,
			Double terrainAngle) {
	}

	public record MeasuredAngle(
			@jakarta.validation.constraints.DecimalMin(value = "0", message = "방향은 0~360도입니다.")
			@jakarta.validation.constraints.DecimalMax(value = "360", message = "방향은 0~360도입니다.") double azimuthDeg,
			@jakarta.validation.constraints.DecimalMin(value = "0", message = "가림 각도는 0~90도입니다.")
			@jakarta.validation.constraints.DecimalMax(value = "90", message = "가림 각도는 0~90도입니다.") double angleDeg) {
	}
}
