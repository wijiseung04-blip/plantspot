package com.springboot.service;

import com.springboot.report.KoreanFont;
import com.springboot.report.PdfWriter;
import com.springboot.report.RoomPlanRenderer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.House;
import com.springboot.domain.Room;
import com.springboot.domain.RoomWindow;
import com.springboot.calculator.Shape;
import com.springboot.security.LoginMember;
import com.springboot.calculator.ObstructionProfile;
import com.springboot.domain.PlantSpecies;
import com.springboot.domain.MyPlant;
import com.springboot.repository.MyPlantRepository;
import com.springboot.calculator.PlantScorer;
import com.springboot.service.RecommendationService.RecommendationView;
import com.springboot.domain.LightLevel;
import com.springboot.domain.SeasonDay;
import com.springboot.calculator.SunlightCalculator.Cell;
import com.springboot.service.SunlightService.SunlightView;
import com.springboot.domain.WeatherHour;

/** 다운로드: 방 리포트 PDF, 계절별 배치도 PNG, 칸별 햇빛 CSV, ASOS CSV. [필수: 파일 다운로드] */
@Service
@Transactional
public class RoomReportService {

	private static final Logger log = LoggerFactory.getLogger(RoomReportService.class);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final HouseService houseService;
	private final SunlightService sunlightService;
	private final ObstructionService obstructionService;
	private final RecommendationService recommendationService;
	private final MyPlantRepository myPlants;
	private final PlantService plantService;
	private final PublicDataService publicData;
	private final KoreanFont font;

	public RoomReportService(HouseService houseService, SunlightService sunlightService,
			ObstructionService obstructionService, RecommendationService recommendationService,
			MyPlantRepository myPlants, PlantService plantService, PublicDataService publicData, KoreanFont font) {
		this.houseService = houseService;
		this.sunlightService = sunlightService;
		this.obstructionService = obstructionService;
		this.recommendationService = recommendationService;
		this.myPlants = myPlants;
		this.plantService = plantService;
		this.publicData = publicData;
		this.font = font;
	}

	// ----- 배치도 이미지 -----

	/** mode: direct(직사광 시간) / level(밝기 단계). plantId가 있으면 그 식물 적합도 색. */
	public byte[] seasonImage(LoginMember me, Long roomId, SeasonDay season, String mode, Long plantId) {
		Room room = houseService.loadRoom(me, roomId);
		return RoomPlanRenderer.png(image(me, room, season, mode, plantId, 900));
	}

	private BufferedImage image(LoginMember me, Room room, SeasonDay season, String mode, Long plantId, int size) {
		List<Cell> cells = sunlightService.season(room, season, SunlightService.currentYear()).cellsAsCalc();
		Function<Cell, Color> color;
		List<RoomPlanRenderer.Mark> marks = new ArrayList<>(myPlantMarks(me, room));
		if (plantId != null) {
			PlantSpecies p = plantService.load(plantId);
			color = c -> RoomPlanRenderer.scoreColor(PlantScorer.score(p, c));
		} else if ("level".equals(mode)) {
			color = RoomPlanRenderer.levelColors();
		} else {
			color = RoomPlanRenderer.directColors(cells);
		}
		return RoomPlanRenderer.render(room.points(), room.getOrientationDeg(), SunlightService.geom(room).windows(),
				cells, 25, color, marks, size);
	}

	private List<RoomPlanRenderer.Mark> myPlantMarks(LoginMember me, Room room) {
		return myPlants.findByRoomId(room.getId()).stream()
				.filter(p -> p.isOwnedBy(room.getHouse().getMember().getId()) && p.getX() != null)
				.map(p -> new RoomPlanRenderer.Mark(p.getX(), p.getY(), new Color(47, 125, 74)))
				.toList();
	}

	// ----- CSV -----

	/** 칸별 햇빛 CSV. date가 있으면 그 날, 없으면 계절 대표일. */
	public String sunlightCsv(LoginMember me, Long roomId, SeasonDay season, LocalDate date) {
		SunlightView v = date != null ? sunlightService.forDate(me, roomId, date, true)
				: sunlightService.season(me, roomId, season != null ? season : SeasonDay.WINTER_SOLSTICE,
						SunlightService.currentYear());
		StringBuilder b = new StringBuilder("﻿"); // 엑셀에서 한글이 깨지지 않도록 BOM
		b.append("label,date,ix,iy,x_cm,y_cm,direct_minutes,lux,level,distance_to_window_cm,calibrated\n");
		for (var c : v.cells()) {
			b.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%.1f,%.1f,%d,%.0f,%s,%.0f,%s%n", v.label(), v.date(), c.ix(),
					c.iy(), c.x(), c.y(), c.directMinutes(), c.lux(), c.level(), c.distance(), v.calibrated()));
		}
		return b.toString();
	}

	/** ASOS 시간자료 CSV */
	public String weatherCsv(LocalDate from, LocalDate to) {
		if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
			throw new IllegalArgumentException("기간은 1년 이내여야 합니다.");
		}
		StringBuilder b = new StringBuilder("﻿");
		b.append("station,observed_at,temperature_c,humidity_pct,sunshine_hours,insolation_mj,cloud_cover,sunshine_qc,clear\n");
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			for (WeatherHour h : publicData.hours(d, publicData.defaultStation())) {
				b.append(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%s,%s,%s,%s%n", h.getStationId(), h.getObservedAt(),
						n(h.getTemperature()), n(h.getHumidity()), n(h.getSunshineHours()), n(h.getInsolation()),
						n(h.getCloudCover()), h.getSunshineQc() == null ? "" : h.getSunshineQc(), h.isClear()));
			}
		}
		return b.toString();
	}

	private static String n(Double v) {
		return v == null ? "" : String.valueOf(v);
	}

	// ----- PDF 리포트 -----

	public byte[] report(LoginMember me, Long roomId) {
		Room room = houseService.loadRoom(me, roomId);
		House house = room.getHouse();
		int year = SunlightService.currentYear();
		try (PdfWriter pdf = new PdfWriter(font)) {
			pdf.title("plantspot 햇빛·식물 리포트");
			pdf.muted(house.getName() + " · " + room.getName() + " · 만든 날 "
					+ LocalDateTime.now(SunService.SEOUL).format(STAMP));

			pdf.heading("1. 집과 방");
			String address = house.getBuilding() != null && house.getBuilding().getLegalDongName() != null
					? house.getBuilding().getLegalDongName() : house.getDong();
			pdf.paragraph(String.format("%s %s, %d층 (층 높이 %.1fm) · 위도 %.4f, 경도 %.4f", address, house.getJibun(), house.getFloor(), house.getFloorHeight(), house.getLatitude(), house.getLongitude()));
			if (house.getBuilding() != null) {
				pdf.paragraph(String.format("우리 건물 높이 %.1fm (높이 출처: %s)", house.getBuilding().getHeight(),
						house.getBuilding().getHeightSource()));
			}
			pdf.paragraph(String.format("방 넓이 %.1f㎡ · 천장 %.0fcm · 커튼 %s · 평면도 위쪽 방향 %.0f°%s",
					Math.abs(Shape.signedArea(room.points())) / 10_000, room.getCeilingHeight(),
					room.getCurtain().getLabel(), room.getOrientationDeg(),
					room.getWinterMinTemp() != null ? String.format(" · 겨울 최저 실내 %.1f℃", room.getWinterMinTemp()) : ""));
			List<String[]> windows = new ArrayList<>();
			windows.add(new String[] { "창문", "방향", "폭 × 높이", "창턱", "창 앞 평균 가림 각도" });
			ObstructionProfile profile = obstructionService.profile(house);
			int i = 1;
			for (RoomWindow w : room.getWindows()) {
				windows.add(new String[] { String.valueOf(i++),
						String.format("%s (%.0f°)", Shape.directionName(w.getAzimuthDeg()), w.getAzimuthDeg()),
						String.format("%.0f × %.0fcm", w.getWidth(), w.getHeight()), String.format("%.0fcm", w.getSillHeight()),
						String.format("%.1f°", profile.averageAngle(w.getAzimuthDeg(), 45)) });
			}
			if (room.getWindows().isEmpty()) {
				pdf.paragraph("등록된 창문이 없습니다.");
			} else {
				pdf.table(windows, new float[] { 1, 2, 2, 1.2f, 2 });
			}
			pdf.muted(profile.hasSensorData() ? "가림 각도: 공공 건물 데이터 + 창틀 센서 실측 보정 반영"
					: "가림 각도: 공공 건물 데이터(국토부 GIS 건물통합정보)로 계산 — 센서 보정 전");

			if (!room.getWindows().isEmpty()) {
				pdf.heading("2. 계절별 햇빛");
				List<String[]> rows = new ArrayList<>();
				rows.add(new String[] { "계절", "낮 길이", "창별 해 드는 시간", "가장 오래 드는 칸", "밝음 / 보통 / 어두움" });
				List<BufferedImage> images = new ArrayList<>();
				List<String> captions = new ArrayList<>();
				for (SeasonDay s : SeasonDay.values()) {
					SunlightView v = sunlightService.season(room, s, year);
					long total = Math.max(1, v.cells().size());
					rows.add(new String[] { s.getLabel() + " (" + v.date() + ")", hm(v.daylightMinutes()),
							String.join(", ", v.windows().stream().map(w -> w.direction() + " " + hm(w.sunMinutes())).toList()),
							hm(v.maxDirectMinutes()),
							String.format("%d%% / %d%% / %d%%", v.levelCounts().get(LightLevel.BRIGHT) * 100 / total,
									v.levelCounts().get(LightLevel.MEDIUM) * 100 / total,
									v.levelCounts().get(LightLevel.DIM) * 100 / total) });
					images.add(image(me, room, s, "direct", null, 420));
					captions.add(s.getLabel() + " 직사광 지도");
				}
				pdf.table(rows, new float[] { 2, 1.5f, 3, 1.6f, 2 });
				pdf.images(images.subList(0, 2), captions.subList(0, 2), (pdf.width() - 10) / 2);
				pdf.images(images.subList(2, 4), captions.subList(2, 4), (pdf.width() - 10) / 2);
				pdf.muted("노란 칸 = 하루 직사광이 드는 칸(진할수록 오래), 회청색 = 직사광 없는 칸(진할수록 어두움), 파란 선 = 창문, 초록 점 = 내 식물, 화살표 = 북쪽");

				pdf.heading("3. 식물 추천");
				try {
					RecommendationView rec = recommendationService.recommendations(me, roomId);
					if (rec.items().isEmpty()) {
						pdf.paragraph("식물 정보가 아직 없습니다.");
					}
					List<String[]> recRows = new ArrayList<>();
					recRows.add(new String[] { "순위", "식물", "점수", "자리", "참고" });
					for (var r : rec.items()) {
						recRows.add(new String[] { String.valueOf(r.rank()), r.name(), String.format("%.0f", r.score()),
								(r.type() == PlantScorer.SpotType.FIXED ? "고정: " : "계절 이동: ")
										+ String.join(", ", r.spots().stream().map(sp -> String.format("%s (%.0f, %.0f)cm",
												sp.label(), sp.x(), sp.y())).toList()),
								String.join(" ", r.notes()) });
					}
					if (recRows.size() > 1) {
						pdf.table(recRows, new float[] { 0.7f, 2, 0.8f, 3.5f, 3.5f });
					}
				} catch (IllegalArgumentException e) {
					pdf.paragraph(e.getMessage());
				}

				List<MyPlant> mine = myPlants.findByRoomId(room.getId());
				if (!mine.isEmpty()) {
					pdf.heading("4. 이 방의 내 식물");
					for (MyPlant p : mine) {
						String line = p.getNickname() + (p.getPlant() != null ? " (" + p.getPlant().getName() + ")" : "");
						if (p.getPlant() != null && p.getX() != null) {
							List<String> scores = new ArrayList<>();
							for (SeasonDay s : SeasonDay.values()) {
								List<Cell> cells = sunlightService.season(room, s, year).cellsAsCalc();
								Cell c = cells.stream().min((a, b) -> Double.compare(Math.hypot(a.x() - p.getX(), a.y() - p.getY()),
										Math.hypot(b.x() - p.getX(), b.y() - p.getY()))).orElseThrow();
								scores.add(s.getLabel() + " " + Math.round(PlantScorer.score(p.getPlant(), c)) + "점");
							}
							line += String.format(" — 자리 (%.0f, %.0f)cm: %s", p.getX(), p.getY(), String.join(", ", scores));
						}
						pdf.bullet(line);
					}
				}
			}

			pdf.heading("계산 방법과 출처");
			pdf.bullet("해 위치: NOAA 태양 위치 공식 (한국천문연구원 출몰시각과 비교 검증)");
			pdf.bullet("가림 각도: 국토교통부 GIS 건물통합정보(사하구) 높이·외곽선, 높이 없으면 층수 × 용도별 층고");
			pdf.bullet("직사광: 15분 간격으로 창 네 모서리를 바닥에 투영해 25cm 칸별 합산");
			pdf.bullet("하늘빛 조도: CIE 흐린 하늘 모델 하늘 성분 + BRE 내부 반사 성분 (추정값, 방 안 센서로 보정)");
			pdf.bullet("식물 정보: 농촌진흥청 농사로 실내정원용 식물 (광요구도·배치장소·겨울 최저온도)");
			byte[] bytes = pdf.toBytes();
			log.info("리포트 PDF: 방 {} ({}바이트)", roomId, bytes.length);
			return bytes;
		} catch (IOException e) {
			throw new UncheckedIOException("PDF를 만들지 못했습니다.", e);
		}
	}

	private static String hm(int minutes) {
		return minutes / 60 + "시간 " + minutes % 60 + "분";
	}
}
