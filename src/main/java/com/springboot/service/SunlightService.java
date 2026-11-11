package com.springboot.service;

import com.springboot.calculator.SunlightCalculator;
import com.springboot.domain.LightLevel;
import com.springboot.domain.SeasonDay;
import com.springboot.domain.SunlightCell;
import com.springboot.domain.SunlightSummary;
import com.springboot.repository.SunlightCellRepository;
import com.springboot.repository.SunlightSummaryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.House;
import com.springboot.event.HouseEvents;
import com.springboot.repository.HouseRepository;
import com.springboot.domain.Room;
import com.springboot.domain.RoomWindow;
import com.springboot.calculator.Shape;
import com.springboot.security.LoginMember;
import com.springboot.calculator.ObstructionProfile;
import com.springboot.calculator.SolarCalculator;
import com.springboot.calculator.SunlightCalculator.Cell;
import com.springboot.calculator.SunlightCalculator.Result;
import com.springboot.calculator.SunlightCalculator.RoomGeom;

/** 칸별 햇빛 계산·저장. 계절 대표일 결과는 저장하고, 다른 날짜는 그때그때 계산한다. */
@Service
@Transactional(readOnly = true)
public class SunlightService {

	private static final Logger log = LoggerFactory.getLogger(SunlightService.class);

	private final SunlightCellRepository cells;
	private final SunlightSummaryRepository summaries;
	private final HouseService houseService;
	private final HouseRepository houses;
	private final ObstructionService obstructionService;
	private final SunlightCalculator calculator;

	public SunlightService(SunlightCellRepository cells, SunlightSummaryRepository summaries,
			HouseService houseService, HouseRepository houses, ObstructionService obstructionService,
			SolarCalculator solar) {
		this.cells = cells;
		this.summaries = summaries;
		this.houseService = houseService;
		this.houses = houses;
		this.obstructionService = obstructionService;
		this.calculator = new SunlightCalculator(solar);
	}

	public static int currentYear() {
		return LocalDate.now(SunService.SEOUL).getYear();
	}

	// ----- 조회·계산 -----

	/** 계절 대표일 4개를 다시 계산해 저장한다. */
	@Transactional
	public List<SunlightView> calculateSeasons(LoginMember me, Long roomId, int year) {
		Room room = houseService.loadRoom(me, roomId);
		List<SunlightView> result = new ArrayList<>();
		for (SeasonDay s : SeasonDay.values()) {
			result.add(store(room, s, year));
		}
		log.info("계절별 햇빛 계산: 방 {} ({}년)", roomId, year);
		return result;
	}

	/** 저장된 계절 결과 (없으면 계산해서 저장). */
	@Transactional
	public SunlightView season(LoginMember me, Long roomId, SeasonDay season, int year) {
		return season(houseService.loadRoom(me, roomId), season, year);
	}

	@Transactional
	public SunlightView season(Room room, SeasonDay season, int year) {
		var summary = summaries.findByRoomIdAndSeasonAndYear(room.getId(), season, year);
		if (summary.isEmpty()) {
			return store(room, season, year);
		}
		List<Cell> stored = cells.findByRoomIdAndSeasonAndYearOrderByIyAscIxAsc(room.getId(), season, year).stream()
				.map(SunlightCell::toCell).toList();
		SunlightSummary s = summary.get();
		return SunlightView.of(room, season, s.getDate(), s.isCalibrated(), SunlightCalculator.DEFAULT_CELL_CM,
				s.getDaylightMinutes(), s.getOutdoorLux(), decodeWindowMinutes(room, s.getWindowMinutes()), stored,
				s.getComputedAt().toString());
	}

	/** 아무 날짜나 바로 계산 (저장하지 않음). calibrated=false 이면 센서 보정 전(건물 데이터만). */
	@Transactional
	public SunlightView forDate(LoginMember me, Long roomId, LocalDate date, boolean calibrated) {
		Room room = houseService.loadRoom(me, roomId);
		WindowProfiles profile = profile(room, calibrated);
		Result r = compute(room, date, profile);
		return SunlightView.of(room, null, date, calibrated && profile.hasSensorData(), r.cellSize(),
				r.daylightMinutes(), r.outdoorLux(), windowMinutes(room, r), r.cells(), null);
	}

	/** 다른 서비스(추천 등)가 쓰는 계절별 칸 결과. */
	@Transactional
	public Map<SeasonDay, List<Cell>> seasonCells(Room room, int year) {
		Map<SeasonDay, List<Cell>> map = new EnumMap<>(SeasonDay.class);
		for (SeasonDay s : SeasonDay.values()) {
			map.put(s, season(room, s, year).cellsAsCalc());
		}
		return map;
	}

	private SunlightView store(Room room, SeasonDay season, int year) {
		LocalDate date = season.date(year);
		WindowProfiles profile = profile(room, true);
		Result r = compute(room, date, profile);

		cells.deleteAll(cells.findByRoomIdAndSeasonAndYearOrderByIyAscIxAsc(room.getId(), season, year));
		summaries.findByRoomIdAndSeasonAndYear(room.getId(), season, year).ifPresent(summaries::delete);
		cells.flush();
		cells.saveAll(r.cells().stream().map(c -> new SunlightCell(room, season, year, c)).toList());
		List<RoomWindow> ws = room.getWindows();
		java.util.StringJoiner joiner = new java.util.StringJoiner(",");
		for (int i = 0; i < ws.size(); i++) {
			joiner.add(ws.get(i).getId() + ":" + r.windowSunMinutes().get(i));
		}
		String encoded = joiner.toString();
		SunlightSummary saved = summaries.save(new SunlightSummary(room, season, year, date, r.daylightMinutes(),
				r.outdoorLux(), encoded, profile.hasSensorData()));
		return SunlightView.of(room, season, date, profile.hasSensorData(), r.cellSize(), r.daylightMinutes(),
				r.outdoorLux(), windowMinutes(room, r), r.cells(), saved.getComputedAt().toString());
	}

	/** 창별 가림 각도 (창 id → 프로필). 창마다 우리 건물 외벽의 관측점이 다르다. */
	private record WindowProfiles(Map<Long, ObstructionProfile> byWindow, boolean hasSensorData) {
	}

	private WindowProfiles profile(Room room, boolean calibrated) {
		Map<Long, ObstructionProfile> map = new java.util.HashMap<>();
		boolean sensor = false;
		for (RoomWindow w : room.getWindows()) {
			ObstructionProfile p = obstructionService.windowProfile(room.getHouse(), w.getAzimuthDeg());
			sensor |= p.hasSensorData();
			map.put(w.getId(), calibrated ? p : p.withoutSensor());
		}
		return new WindowProfiles(map, calibrated && sensor);
	}

	private Result compute(Room room, LocalDate date, WindowProfiles profile) {
		House h = room.getHouse();
		return calculator.calculatePerWindow(geom(room), h.getLatitude(), h.getLongitude(), date,
				w -> profile.byWindow().get(w.id()), SunlightCalculator.Options.defaults());
	}

	public static RoomGeom geom(Room room) {
		List<double[]> ws = room.getWindows().stream()
				.map(w -> new double[] { w.getId(), w.getWallIndex(), w.getOffset(), w.getWidth(), w.getHeight(),
						w.getSillHeight(), w.getAzimuthDeg() })
				.toList();
		return SunlightCalculator.room(room.points(), room.getOrientationDeg(), room.getCeilingHeight(), ws,
				room.getCurtain().getTransmittance());
	}

	private static List<WindowSun> windowMinutes(Room room, Result r) {
		List<WindowSun> list = new ArrayList<>();
		for (int i = 0; i < room.getWindows().size(); i++) {
			RoomWindow w = room.getWindows().get(i);
			list.add(new WindowSun(w.getId(), Shape.directionName(w.getAzimuthDeg()), r.windowSunMinutes().get(i)));
		}
		return list;
	}

	private static List<WindowSun> decodeWindowMinutes(Room room, String encoded) {
		Map<Long, Integer> minutes = new java.util.HashMap<>();
		if (encoded != null && !encoded.isBlank()) {
			for (String part : encoded.split(",")) {
				String[] kv = part.split(":");
				minutes.put(Long.valueOf(kv[0]), Integer.valueOf(kv[1]));
			}
		}
		return room.getWindows().stream()
				.map(w -> new WindowSun(w.getId(), Shape.directionName(w.getAzimuthDeg()),
						minutes.getOrDefault(w.getId(), 0)))
				.toList();
	}

	// ----- 낡은 결과 지우기 -----

	@EventListener
	public void onRoomChanged(HouseEvents.RoomChanged e) {
		clearRoom(e.roomId());
	}

	@EventListener
	public void onRoomDeleting(HouseEvents.RoomDeleting e) {
		clearRoom(e.roomId());
	}

	@EventListener
	public void onHouseChanged(HouseEvents.HouseChanged e) {
		clearHouse(e.houseId());
	}

	@EventListener
	public void onObstructionChanged(ObstructionService.ObstructionChanged e) {
		clearHouse(e.houseId());
	}

	private void clearHouse(Long houseId) {
		houses.findById(houseId).ifPresent(h -> h.getRooms().forEach(r -> clearRoom(r.getId())));
	}

	private void clearRoom(Long roomId) {
		cells.deleteByRoom(roomId);
		summaries.deleteByRoom(roomId);
	}

	// ----- 응답 -----

	public record WindowSun(Long windowId, String direction, int sunMinutes) {
	}

	public record CellView(int ix, int iy, double x, double y, int directMinutes, double lux, double distance,
			LightLevel level) {
	}

	public record SunlightView(Long roomId, SeasonDay season, String label, LocalDate date, boolean calibrated,
			double cellSize, int daylightMinutes, double outdoorLux, int maxDirectMinutes,
			Map<LightLevel, Long> levelCounts, List<WindowSun> windows, List<CellView> cells, String computedAt) {

		static SunlightView of(Room room, SeasonDay season, LocalDate date, boolean calibrated, double cellSize,
				int daylightMinutes, double outdoorLux, List<WindowSun> windows, List<Cell> cells, String computedAt) {
			Map<LightLevel, Long> counts = new EnumMap<>(LightLevel.class);
			for (LightLevel l : LightLevel.values()) {
				counts.put(l, 0L);
			}
			counts.putAll(cells.stream().collect(Collectors.groupingBy(Cell::level, Collectors.counting())));
			return new SunlightView(room.getId(), season, season != null ? season.getLabel() : date.toString(), date,
					calibrated, cellSize, daylightMinutes, outdoorLux,
					cells.stream().mapToInt(Cell::directMinutes).max().orElse(0), counts, windows,
					cells.stream().map(c -> new CellView(c.ix(), c.iy(), c.x(), c.y(), c.directMinutes(), c.lux(),
							c.distanceToWindow(), c.level())).toList(),
					computedAt);
		}

		/** 다른 서비스용: 칸 결과를 계산기 Cell로 */
		public List<Cell> cellsAsCalc() {
			return cells.stream().map(c -> new Cell(c.ix(), c.iy(), c.x(), c.y(), c.directMinutes(), c.lux(), 0,
					c.distance(), c.level())).toList();
		}
	}
}
