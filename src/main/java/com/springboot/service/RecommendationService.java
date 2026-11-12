package com.springboot.service;

import com.springboot.calculator.PlantScorer;
import com.springboot.domain.Recommendation;
import com.springboot.repository.RecommendationRepository;

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

import com.springboot.event.HouseEvents;
import com.springboot.repository.HouseRepository;
import com.springboot.domain.Room;
import com.springboot.security.LoginMember;
import com.springboot.event.PlantEvents;
import com.springboot.domain.PlantSpecies;
import com.springboot.calculator.PlantScorer.PlantResult;
import com.springboot.calculator.PlantScorer.Spot;
import com.springboot.calculator.PlantScorer.SpotType;
import com.springboot.domain.SeasonDay;
import com.springboot.calculator.SunlightCalculator.Cell;

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
					p.getLightNames(), p.getManageLevelCode(), p.getManageLevelName(), first.getSpotType(),
					first.getScore(), null, spots,
					first.getNotes() == null || first.getNotes().isBlank() ? List.of()
							: java.util.Arrays.stream(first.getNotes().split("\n"))
									.filter(n -> !n.startsWith("1년 내내 잘 맞는(")) // 예전에 주의 사항으로 저장된 안내
									.toList()));
		}
		return new RecommendationView(room.getId(), room.getName(), plantService.count(), list,
				rows.isEmpty() ? null : rows.get(0).getComputedAt().toString());
	}

	/**
	 * 이 방에서 모든 식물의 점수와 순위 (저장하지 않음). 화면에서 관리 수준으로 거르거나 이름으로 찾아
	 * 상위 추천에 들지 않은 식물의 점수도 볼 수 있게 한다.
	 */
	@Transactional
	public RecommendationView allScores(LoginMember me, Long roomId) {
		Room room = houseService.loadRoom(me, roomId);
		if (room.getWindows().isEmpty()) {
			throw new IllegalArgumentException("창문이 없는 방입니다. 창문을 먼저 등록하세요.");
		}
		Map<SeasonDay, List<Cell>> seasons = sunlightService.seasonCells(room, SunlightService.currentYear());
		List<PlantResult> results = plantService.all().stream()
				.map(p -> PlantScorer.evaluate(p, seasons, room.getWinterMinTemp()))
				.sorted(Comparator.comparingDouble(PlantResult::score).reversed()
						.thenComparing(r -> r.plant().getName(), Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		List<PlantRecommendation> list = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			PlantResult r = results.get(i);
			PlantSpecies p = r.plant();
			List<SpotView> spots = r.type() == SpotType.FIXED
					? List.of(new SpotView(null, "1년 내내", r.fixed().cell().x(), r.fixed().cell().y(), r.fixed().score()))
					: r.seasonal().values().stream()
							.map(s -> new SpotView(s.season(), s.season().getLabel(), s.cell().x(), s.cell().y(), s.score()))
							.toList();
			list.add(new PlantRecommendation(i + 1, p.getId(), p.getName(), p.getThumbnailUrl(), p.getLightNames(),
					p.getManageLevelCode(), p.getManageLevelName(), r.type(), r.score(), percent(r.roomyShare()), spots,
					r.notes()));
		}
		return new RecommendationView(room.getId(), room.getName(), list.size(), list, null);
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
		return new PlantFit(p.getId(), p.getName(), r.type(), r.score(), percent(r.roomyShare()), r.notes(), map,
				breakdown(r));
	}

	private static BreakdownView breakdown(PlantResult r) {
		PlantScorer.Breakdown b = r.breakdown();
		List<SeasonPartView> seasons = b.seasons().stream()
				.map(sp -> new SeasonPartView(sp.season(), sp.season().getLabel(), percent(sp.parts().light()),
						percent(sp.parts().direct()), percent(sp.parts().distance()), sp.parts().score()))
				.toList();
		return new BreakdownView(seasons, b.spotScore(), b.roomyBonus(), b.temperatureFactor(), b.total());
	}

	private static int percent(double share) {
		return (int) Math.round(share * 100);
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

	/** manageLevelCode: 농사로 관리 수준 (089001 초보자 / 089002 경험자 / 089003 전문가, 없으면 null) */
	public record PlantRecommendation(int rank, Long plantId, String name, String thumbnailUrl, String lightNames,
			String manageLevelCode, String manageLevelName, SpotType type, double score,
			/** 1년 내내 잘 맞는(80점 이상) 칸이 방에서 차지하는 비율(%), 저장된 추천은 null */ Integer roomyPercent,
			List<SpotView> spots, List<String> notes) {
	}

	public record RecommendationView(Long roomId, String roomName, long plantCount, List<PlantRecommendation> items,
			String computedAt) {
	}

	public record CellScore(double x, double y, double score, boolean withinPlacement) {
	}

	public record PlantFit(Long plantId, String name, SpotType type, double score, int roomyPercent, List<String> notes,
			Map<SeasonDay, List<CellScore>> seasons, BreakdownView breakdown) {
	}

	/** 추천 자리의 계절별 항목 점수: 빛·직사광·창 거리(%, 100이면 감점 없음)와 칸 점수 */
	public record SeasonPartView(SeasonDay season, String label, int light, int direct, int distance, double score) {
	}

	/** 점수 내역: total = spotScore × 0.9 + roomyBonus, 겨울 온도가 모자라면 × temperatureFactor(0.7) */
	public record BreakdownView(List<SeasonPartView> seasons, double spotScore, double roomyBonus,
			double temperatureFactor, double total) {
	}
}
