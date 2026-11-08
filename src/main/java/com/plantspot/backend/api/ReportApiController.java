package com.plantspot.backend.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.report.RoomReportService;
import com.plantspot.backend.sunlight.SeasonDay;

/** 다운로드: 리포트 PDF, 계절별 배치도 PNG, 칸별 햇빛 CSV, ASOS CSV. [필수: 파일 다운로드] */
@RestController
public class ReportApiController {

	private final RoomReportService service;

	public ReportApiController(RoomReportService service) {
		this.service = service;
	}

	@GetMapping("/api/rooms/{id}/report")
	public ResponseEntity<byte[]> report(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return file(service.report(me, id), MediaType.APPLICATION_PDF, "plantspot-room-" + id + "-report.pdf", false);
	}

	/** 계절별 배치도. mode=direct|level, plantId 를 주면 그 식물 적합도 색. download=true 면 내려받기. */
	@GetMapping("/api/rooms/{id}/sunlight/image")
	public ResponseEntity<byte[]> image(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@RequestParam(name = "season", defaultValue = "WINTER_SOLSTICE") SeasonDay season,
			@RequestParam(name = "mode", defaultValue = "direct") String mode, @RequestParam(name = "plantId", required = false) Long plantId,
			@RequestParam(name = "download", defaultValue = "false") boolean download) {
		return file(service.seasonImage(me, id, season, mode, plantId), MediaType.IMAGE_PNG,
				"plantspot-room-" + id + "-" + season.name().toLowerCase() + ".png", !download);
	}

	@GetMapping("/api/rooms/{id}/sunlight/csv")
	public ResponseEntity<byte[]> sunlightCsv(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@RequestParam(name = "season", required = false) SeasonDay season,
			@RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		String name = "plantspot-room-" + id + "-" + (date != null ? date.toString()
				: (season != null ? season : SeasonDay.WINTER_SOLSTICE).name().toLowerCase()) + ".csv";
		return file(service.sunlightCsv(me, id, season, date).getBytes(StandardCharsets.UTF_8),
				new MediaType("text", "csv", StandardCharsets.UTF_8), name, false);
	}

	@GetMapping("/api/weather/hours/csv")
	public ResponseEntity<byte[]> weatherCsv(@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return file(service.weatherCsv(from, to).getBytes(StandardCharsets.UTF_8),
				new MediaType("text", "csv", StandardCharsets.UTF_8), "asos-" + from + "-" + to + ".csv", false);
	}

	private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String filename, boolean inline) {
		ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(filename, StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
				.header("X-Content-Type-Options", "nosniff").body(body);
	}
}
