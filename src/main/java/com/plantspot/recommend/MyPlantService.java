package com.plantspot.recommend;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.common.ForbiddenException;
import com.plantspot.common.NotFoundException;
import com.plantspot.file.FileService;
import com.plantspot.house.HouseEvents;
import com.plantspot.house.HouseService;
import com.plantspot.house.Room;
import com.plantspot.member.LoginMember;
import com.plantspot.member.MemberService;
import com.plantspot.plant.PlantEvents;
import com.plantspot.plant.PlantService;
import com.plantspot.plant.PlantSpecies;
import com.plantspot.sun.SunService;
import com.plantspot.sunlight.SeasonDay;
import com.plantspot.sunlight.SunlightCalculator.Cell;
import com.plantspot.sunlight.SunlightService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 내 식물 등록·위치 이동·알림. */
@Service
@Transactional(readOnly = true)
public class MyPlantService {

	private static final Logger log = LoggerFactory.getLogger(MyPlantService.class);
	/** 이 점수보다 낮으면 알림 */
	public static final double ALERT_SCORE = 50;

	private final MyPlantRepository repository;
	private final MemberService memberService;
	private final PlantService plantService;
	private final HouseService houseService;
	private final FileService fileService;
	private final SunlightService sunlightService;

	public MyPlantService(MyPlantRepository repository, MemberService memberService, PlantService plantService,
			HouseService houseService, FileService fileService, SunlightService sunlightService) {
		this.repository = repository;
		this.memberService = memberService;
		this.plantService = plantService;
		this.houseService = houseService;
		this.fileService = fileService;
		this.sunlightService = sunlightService;
	}

	public record SaveMyPlant(Long plantId,
			@NotBlank(message = "별명을 입력하세요.") @Size(max = 40, message = "별명은 40자 이하입니다.") String nickname,
			@Size(max = 500, message = "메모는 500자 이하입니다.") String memo, Long photoFileId, Long roomId, Double x,
			Double y) {
	}

	public record Move(Long roomId, Double x, Double y) {
	}

	@Transactional
	public MyPlantView create(LoginMember me, SaveMyPlant req) {
		MyPlant p = new MyPlant(memberService.get(me.getId()), plant(req.plantId()), req.nickname().trim());
		p.update(plant(req.plantId()), req.nickname().trim(), req.memo(), photo(me, req.photoFileId()));
		p.moveTo(room(me, req.roomId()), req.x(), req.y());
		repository.save(p);
		log.info("내 식물 등록: {} (회원 {})", p.getNickname(), me.getUsername());
		return view(p);
	}

	@Transactional // 햇빛 결과가 없으면 계산해서 저장하므로 쓰기 트랜잭션
	public List<MyPlantView> list(LoginMember me) {
		return repository.findByOwnerIdOrderByIdAsc(me.getId()).stream().map(this::view).toList();
	}

	@Transactional
	public MyPlantView update(LoginMember me, Long id, SaveMyPlant req) {
		MyPlant p = load(me, id);
		p.update(plant(req.plantId()), req.nickname().trim(), req.memo(), photo(me, req.photoFileId()));
		p.moveTo(room(me, req.roomId()), req.x(), req.y());
		return view(p);
	}

	@Transactional
	public MyPlantView move(LoginMember me, Long id, Move req) {
		MyPlant p = load(me, id);
		p.moveTo(room(me, req.roomId()), req.x(), req.y());
		return view(p);
	}

	@Transactional
	public void delete(LoginMember me, Long id) {
		repository.delete(load(me, id));
	}

	/** 알림: 지금·다음 계절에 현재 자리 점수가 낮은 식물, 겨울 온도 경고, 자리를 정하지 않은 식물. */
	@Transactional
	public List<Alert> alerts(LoginMember me) {
		SeasonDay now = Seasons.of(LocalDate.now(SunService.SEOUL));
		SeasonDay next = Seasons.next(now);
		List<Alert> alerts = new ArrayList<>();
		for (MyPlant p : repository.findByOwnerIdOrderByIdAsc(me.getId())) {
			if (p.getPlant() == null) {
				continue;
			}
			if (p.getRoom() == null || p.getX() == null) {
				alerts.add(new Alert(p.getId(), p.getNickname(), "INFO",
						"놓은 자리를 정하면 계절별로 알맞은지 알려드립니다.", null, null, null));
				continue;
			}
			Room room = p.getRoom();
			if (room.getWindows().isEmpty()) {
				continue;
			}
			Map<SeasonDay, List<Cell>> seasons = sunlightService.seasonCells(room, SunlightService.currentYear());
			PlantScorer.PlantResult best = PlantScorer.evaluate(p.getPlant(), seasons, room.getWinterMinTemp());
			for (SeasonDay s : List.of(now, next)) {
				Cell here = nearest(seasons.get(s), p.getX(), p.getY());
				double score = PlantScorer.score(p.getPlant(), here);
				if (score < ALERT_SCORE) {
					PlantScorer.Spot better = best.type() == PlantScorer.SpotType.FIXED ? best.fixed() : best.seasonal().get(s);
					alerts.add(new Alert(p.getId(), p.getNickname(), "MOVE",
							String.format("%s%s 현재 자리 점수 %.0f점 — 더 알맞은 자리로 옮겨 보세요.", s == now ? "지금(" : "다음 계절(",
									s.getLabel() + ")", score),
							s, better != null ? better.cell().x() : null, better != null ? better.cell().y() : null));
					break;
				}
			}
			if (p.getPlant().getWinterMinTemp() != null && room.getWinterMinTemp() != null
					&& room.getWinterMinTemp() < p.getPlant().getWinterMinTemp()) {
				alerts.add(new Alert(p.getId(), p.getNickname(), "TEMP",
						String.format("겨울 실내 최저 %.1f℃가 %s의 겨울 최저온도 %d℃보다 낮습니다.", room.getWinterMinTemp(),
								p.getPlant().getName(), p.getPlant().getWinterMinTemp()),
						SeasonDay.WINTER_SOLSTICE, null, null));
			}
		}
		return alerts;
	}

	static Cell nearest(List<Cell> cells, double x, double y) {
		return cells.stream().min(Comparator.comparingDouble(c -> Math.hypot(c.x() - x, c.y() - y))).orElseThrow();
	}

	private MyPlant load(LoginMember me, Long id) {
		MyPlant p = repository.findById(id).orElseThrow(() -> new NotFoundException("내 식물을 찾을 수 없습니다."));
		if (!p.isOwnedBy(me.getId()) && !me.isAdmin()) {
			throw new ForbiddenException("다른 회원의 식물입니다.");
		}
		return p;
	}

	private PlantSpecies plant(Long plantId) {
		return plantId == null ? null : plantService.load(plantId);
	}

	private Room room(LoginMember me, Long roomId) {
		return roomId == null ? null : houseService.loadRoom(me, roomId);
	}

	private Long photo(LoginMember me, Long fileId) {
		if (fileId != null) {
			fileService.info(me, fileId); // 본인 파일인지 확인
		}
		return fileId;
	}

	private MyPlantView view(MyPlant p) {
		Double score = null;
		if (p.getPlant() != null && p.getRoom() != null && p.getX() != null && !p.getRoom().getWindows().isEmpty()) {
			SeasonDay now = Seasons.of(LocalDate.now(SunService.SEOUL));
			var cells = sunlightService.season(p.getRoom(), now, SunlightService.currentYear()).cellsAsCalc();
			score = PlantScorer.score(p.getPlant(), nearest(cells, p.getX(), p.getY()));
		}
		return new MyPlantView(p.getId(), p.getNickname(), p.getPlant() != null ? p.getPlant().getId() : null,
				p.getPlant() != null ? p.getPlant().getName() : null,
				p.getPlant() != null ? p.getPlant().getThumbnailUrl() : null, p.getRoom() != null ? p.getRoom().getId() : null,
				p.getRoom() != null ? p.getRoom().getName() : null, p.getX(), p.getY(), p.getPhotoFileId(), p.getMemo(),
				score);
	}

	// ----- 정리 -----

	@EventListener
	public void onRoomDeleting(HouseEvents.RoomDeleting e) {
		repository.findByRoomId(e.roomId()).forEach(p -> p.moveTo(null, null, null));
	}

	@EventListener
	public void onPlantDeleting(PlantEvents.PlantDeleting e) {
		repository.findByPlantId(e.plantId()).forEach(p -> p.update(null, p.getNickname(), p.getMemo(), p.getPhotoFileId()));
	}

	// ----- 응답 -----

	public record MyPlantView(Long id, String nickname, Long plantId, String plantName, String thumbnailUrl,
			Long roomId, String roomName, Double x, Double y, Long photoFileId, String memo, Double currentScore) {
	}

	/** type: MOVE(자리 옮기기) / TEMP(겨울 온도) / INFO */
	public record Alert(Long myPlantId, String nickname, String type, String message, SeasonDay season,
			Double suggestX, Double suggestY) {
	}
}
