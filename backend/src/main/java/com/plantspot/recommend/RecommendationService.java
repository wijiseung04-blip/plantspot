package com.plantspot.recommend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.house.HouseEvents;
import com.plantspot.house.HouseRepository;
import com.plantspot.house.HouseService;
import com.plantspot.house.Room;
import com.plantspot.member.LoginMember;
import com.plantspot.obstruction.ObstructionService;
import com.plantspot.plant.PlantEvents;
import com.plantspot.plant.PlantService;
import com.plantspot.plant.PlantSpecies;
import com.plantspot.recommend.PlantScorer.PlantResult;
import com.plantspot.recommend.PlantScorer.Spot;
import com.plantspot.recommend.PlantScorer.SpotType;
import com.plantspot.sunlight.SeasonDay;
import com.plantspot.sunlight.SunlightCalculator.Cell;
import com.plantspot.sunlight.SunlightService;

/** 방 식물 추천: 계절별 칸 햇빛 × 식물 변환값 → 점수, 상위 식물과 자리 저장. */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
	public static final int DEFAULT_LIMIT = 10;

	private final RecommendationRepository repository;
	private final HouseService houseService;
	private final HouseRepository houses;
	private final SunlightService sunlightService;
	private final PlantService plantService;

	public RecommendationService(RecommendationRepository repository, HouseService houseService,
			HouseRepository houses, SunlightService sunlightService, PlantService plantService) {
		this.repository = repository;
		this.houseService = houseService;
		this.houses = houses;
		this.sunlightService = sunlightService;
		this.plantService = plantService;
	}

	/** 저장된 추천(없으면 계산해서 저장). */
	@Transactional
	public RecommendationView recommendations(LoginMember me, Long roomId) {
		Room room = houseService.loadRoom(me, roomId);
		List<Recommendation> stored = repository.findByRoomIdOrderByRankAscSeasonAsc(roomId);
		if (stored.isEmpty()) {
			return refresh(room, DEFAULT_LIMIT);
		}
		return fromStored(room, stored);
	}

	@Transactional
	public RecommendationView refresh(LoginMember me, Long roomId, int limit) {
		return refresh(houseService.loadRoom(me, roomId), limit);
	}

	private RecommendationView refresh(Room room, int limit) {
		if (room.getWindows().isEmpty()) {
			throw new IllegalArgumentException("창문이 없는 방입니다. 창문을 먼저 등록하세요.");
		}
		int year = SunlightService.currentYear();
		Map<SeasonDay, List<Cell>> seasons = sunlightService.seasonCells(room, year);
		List<PlantResult> results = plantService.all().stream()
				.map(p -> PlantScorer.evaluate(p, seasons, room.getWinterMinTemp()))
				.sorted(Comparator.comparingDouble(PlantResult::score).reversed())
				.limit(Math.max(1, Math.min(limit, 50)))
				.toList();

		repository.deleteByRoom(room.getId());
		List<Recommendation> rows = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			PlantResult r = results.get(i);
			String notes = String.join("\n", r.notes());
			if (r.type() == SpotType.FIXED) {
				rows.add(new Recommendation(room, r.plant(), i + 1, r.type(), null, r.fixed().cell().x(),
						r.fixed().cell().y(), r.score(), r.fixed().score(), notes, year));
			} else {
				for (Spot s : r.seasonal().values()) {
					rows.add(new Recommendation(room, r.plant(), i + 1, r.type(), s.season(), s.cell().x(),
							s.cell().y(), r.score(), s.score(), notes, year));
				}
			}
		}
		repository.saveAll(rows);
		log.info("추천 계산: 방 {} 식물 {}종 중 상위 {}", room.getId(), plantService.count(), results.size());
		return fromStored(room, rows);
	}

	private RecommendationView fromStored(Room room, List<Recommendation> rows) {
		Map<Long, List<Recommendation>> byPlant = new LinkedHashMap<>();
		rows.stream().sorted(Comparator.comparingInt(Recommendation::getRank))
				.forEach(r -> byPlant.computeIfAbsent(r.getPlant().getId(), k -> new ArrayList<>()).add(r));
		List<PlantRecommendation> list = new ArrayList<>();
		for (List<Recommendation> group : byPlant.values()) {
			Recommendation first = group.get(0);
			PlantSpecies p = first.getPlant();
			List<SpotView> spots = group.stream()
					.map(r -> new SpotView(r.getSeason(), r.getSeason() != null ? r.getSeason().getLabel() : "1년 내내",
							r.getCellX(), r.getCellY(), r.getSpotScore()))
					.toList();
			list.add(new PlantRecommendation(first.getRank(), p.getId(), p.getName(), p.getThumbnailUrl(),
					p.getLightNames(), first.getSpotType(), first.getScore(), spots,
					first.getNotes() == null || first.getNotes().isBlank() ? List.of()
							: List.of(first.getNotes().split("\n"))));
		}
		return new RecommendationView(room.getId(), room.getName(), plantService.count(), list,
				rows.isEmpty() ? null : rows.get(0).getComputedAt().toString());
	}

	/** 한 식물의 계절별 칸 점수 (적합도 지도). */
	@Transactional
	public PlantFit fit(LoginMember me, Long roomId, Long plantId) {
		Room room = houseService.loadRoom(me, roomId);
		PlantSpecies p = plantService.load(plantId);
		Map<SeasonDay, List<Cell>> seasons = sunlightService.seasonCells(room, SunlightService.currentYear());
		Map<SeasonDay, List<CellScore>> map = new EnumMap<>(SeasonDay.class);
		seasons.forEach((s, cells) -> map.put(s, cells.stream()
				.map(c -> new CellScore(c.x(), c.y(), PlantScorer.score(p, c), PlantScorer.withinPlacement(p, c)))
				.toList()));
		PlantResult r = PlantScorer.evaluate(p, seasons, room.getWinterMinTemp());
		return new PlantFit(p.getId(), p.getName(), r.type(), r.score(), r.notes(), map);
	}

	// ----- 낡은 결과 지우기 -----

	@EventListener
	public void onRoomChanged(HouseEvents.RoomChanged e) {
		repository.deleteByRoom(e.roomId());
	}

	@EventListener
	public void onRoomDeleting(HouseEvents.RoomDeleting e) {
		repository.deleteByRoom(e.roomId());
	}

	@EventListener
	public void onHouseChanged(HouseEvents.HouseChanged e) {
		houses.findById(e.houseId()).ifPresent(h -> h.getRooms().forEach(r -> repository.deleteByRoom(r.getId())));
	}

	@EventListener
	public void onObstructionChanged(ObstructionService.ObstructionChanged e) {
		houses.findById(e.houseId()).ifPresent(h -> h.getRooms().forEach(r -> repository.deleteByRoom(r.getId())));
	}

	@EventListener
	public void onPlantChanged(PlantEvents.PlantChanged e) {
		// 그 식물이 들어간 방의 추천을 지워 다음 조회 때 다시 계산
		repository.findByPlantId(e.plantId()).stream().map(r -> r.getRoom().getId()).distinct()
				.forEach(repository::deleteByRoom);
	}

	@EventListener
	public void onPlantDeleting(PlantEvents.PlantDeleting e) {
		onPlantChanged(new PlantEvents.PlantChanged(e.plantId()));
	}

	// ----- 응답 -----

	public record SpotView(SeasonDay season, String label, double x, double y, double score) {
	}

	public record PlantRecommendation(int rank, Long plantId, String name, String thumbnailUrl, String lightNames,
			SpotType type, double score, List<SpotView> spots, List<String> notes) {
	}

	public record RecommendationView(Long roomId, String roomName, long plantCount, List<PlantRecommendation> items,
			String computedAt) {
	}

	public record CellScore(double x, double y, double score, boolean withinPlacement) {
	}

	public record PlantFit(Long plantId, String name, SpotType type, double score, List<String> notes,
			Map<SeasonDay, List<CellScore>> seasons) {
	}
}
