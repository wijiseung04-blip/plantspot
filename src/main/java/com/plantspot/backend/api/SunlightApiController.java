package com.plantspot.backend.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.sunlight.SeasonDay;
import com.plantspot.backend.sunlight.SunlightService;
import com.plantspot.backend.sunlight.SunlightService.SunlightView;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 방 칸별 햇빛. */
@RestController
@RequestMapping("/api/rooms/{roomId}/sunlight")
public class SunlightApiController {

	private final SunlightService service;

	public SunlightApiController(SunlightService service) {
		this.service = service;
	}

	/**
	 * GET /api/rooms/{id}/sunlight?season=WINTER_SOLSTICE[&year=2026] — 저장된 계절 결과
	 * GET /api/rooms/{id}/sunlight?date=2026-11-08[&calibrated=false] — 그 날짜 바로 계산
	 * 둘 다 없으면 올해 동지.
	 */
	@GetMapping
	public SunlightView get(@AuthenticationPrincipal LoginMember me, @PathVariable("roomId") Long roomId,
			@RequestParam(name = "season", required = false) SeasonDay season,
			@RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "year", required = false) @Min(value = 1901, message = "연도는 1901~2099입니다.")
			@Max(value = 2099, message = "연도는 1901~2099입니다.") Integer year,
			@RequestParam(name = "calibrated", defaultValue = "true") boolean calibrated) {
		if (date != null) {
			return service.forDate(me, roomId, date, calibrated);
		}
		return service.season(me, roomId, season != null ? season : SeasonDay.WINTER_SOLSTICE,
				year != null ? year : SunlightService.currentYear());
	}

	/** 계절 대표일 4개를 다시 계산해 저장. */
	@PostMapping("/calculate")
	public List<SunlightView> calculate(@AuthenticationPrincipal LoginMember me, @PathVariable("roomId") Long roomId,
			@RequestParam(name = "year", required = false) @Min(value = 1901, message = "연도는 1901~2099입니다.")
			@Max(value = 2099, message = "연도는 1901~2099입니다.") Integer year) {
		return service.calculateSeasons(me, roomId, year != null ? year : SunlightService.currentYear());
	}
}
