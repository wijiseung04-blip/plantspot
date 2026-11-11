package com.springboot.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.exception.NotFoundException;
import com.springboot.service.SunService;
import com.springboot.service.PublicDataService;
import com.springboot.service.PublicDataService.CollectResult;
import com.springboot.service.PublicDataService.Status;
import com.springboot.service.TerrainService;
import com.springboot.service.PublicDataService.SunCompare;
import com.springboot.domain.SunEvent;
import com.springboot.domain.WeatherHour;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** 출몰시각·ASOS 자료 (회원), 수집(관리자). API 사용량 때문에 비회원에게는 열지 않는다. */
@RestController
public class PublicDataApiController {

	private final PublicDataService service;
	private final TerrainService terrain;

	public PublicDataApiController(PublicDataService service, TerrainService terrain) {
		this.service = service;
		this.terrain = terrain;
	}

	/** 지형(땅 높이) 격자 상태 — 가져오기는 서버를 끄고 ./gradlew importTerrain */
	@GetMapping("/api/admin/terrain/status")
	public TerrainService.Status terrainStatus() {
		return terrain.status();
	}

	@GetMapping("/api/sun-events")
	public SunEvent sunEvent(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "lat", required = false) @DecimalMin(value = "-90", message = "위도는 -90~90입니다.")
			@DecimalMax(value = "90", message = "위도는 -90~90입니다.") Double lat,
			@RequestParam(name = "lon", required = false) @DecimalMin(value = "-180", message = "경도는 -180~180입니다.")
			@DecimalMax(value = "180", message = "경도는 -180~180입니다.") Double lon) {
		return service.sunEvent(date, lat != null ? lat : SunService.DEFAULT_LATITUDE,
				lon != null ? lon : SunService.DEFAULT_LONGITUDE)
				.orElseThrow(() -> new NotFoundException("출몰시각 자료가 없습니다(천문연 API 키 필요)."));
	}

	/** 우리 계산 vs 천문연 출몰시각 (정확도 검증) */
	@GetMapping("/api/sun-events/compare")
	public SunCompare compare(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "lat", required = false) Double lat, @RequestParam(name = "lon", required = false) Double lon) {
		return service.compare(date, lat != null ? lat : SunService.DEFAULT_LATITUDE,
				lon != null ? lon : SunService.DEFAULT_LONGITUDE);
	}

	@GetMapping("/api/weather/hours")
	public List<WeatherHour> hours(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "station", required = false) Integer station) {
		return service.hours(date, station != null ? station : service.defaultStation());
	}

	@PostMapping("/api/admin/weather/collect")
	public CollectResult collect(@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "station", required = false) Integer station) {
		if (to != null && to.isBefore(from)) {
			throw new IllegalArgumentException("끝 날짜가 시작 날짜보다 빠릅니다.");
		}
		if (to != null && from.plusDays(62).isBefore(to)) {
			throw new IllegalArgumentException("한 번에 62일까지만 받을 수 있습니다.");
		}
		return service.collectWeather(from, to != null ? to : from, station != null ? station : service.defaultStation());
	}

	@GetMapping("/api/admin/public-data/status")
	public Status status() {
		return service.status();
	}
}
