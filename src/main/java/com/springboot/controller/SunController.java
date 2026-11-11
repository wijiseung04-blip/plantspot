package com.springboot.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.calculator.SolarPosition;
import com.springboot.domain.SeasonDay;
import java.util.List;
import com.springboot.service.SunService;
import com.springboot.service.SunService.SunDay;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** 해 위치 확인용 API. */
@RestController
@RequestMapping("/api/sun")
public class SunController {

	private final SunService sunService;

	public SunController(SunService sunService) {
		this.sunService = sunService;
	}

	/** 예: GET /api/sun?dateTime=2026-12-21T12:30&lat=35.1154&lon=128.9605 (시각은 한국 시각) */
	@GetMapping
	public SunResponse sun(
			@RequestParam("dateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime,
			@RequestParam(name = "lat", required = false) @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
			@DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.") Double lat,
			@RequestParam(name = "lon", required = false) @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
			@DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.") Double lon) {
		double latitude = lat != null ? lat : SunService.DEFAULT_LATITUDE;
		double longitude = lon != null ? lon : SunService.DEFAULT_LONGITUDE;

		SolarPosition p = sunService.position(dateTime, latitude, longitude);
		SunDay day = sunService.day(dateTime.toLocalDate(), latitude, longitude);
		return new SunResponse(dateTime, latitude, longitude, round(p.altitudeDeg()), round(p.azimuthDeg()),
				p.aboveHorizon(), day.sunrise(), day.solarNoon(), day.sunset());
	}

	/**
	 * 계절 대표일(춘분·하지·추분·동지) 해 경로, 15분 간격, 해가 떠 있는 동안만. 창별 스카이라인 그림에 겹쳐 그린다.
	 * 예: GET /api/sun/paths?lat=35.1154&lon=128.9605&year=2026
	 */
	@GetMapping("/paths")
	public List<SunPath> paths(
			@RequestParam(name = "lat", required = false) @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
			@DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.") Double lat,
			@RequestParam(name = "lon", required = false) @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
			@DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.") Double lon,
			@RequestParam(name = "year", required = false) Integer year) {
		double latitude = lat != null ? lat : SunService.DEFAULT_LATITUDE;
		double longitude = lon != null ? lon : SunService.DEFAULT_LONGITUDE;
		int y = year != null ? year : java.time.LocalDate.now(SunService.SEOUL).getYear();
		List<SunPath> list = new java.util.ArrayList<>();
		for (SeasonDay s : SeasonDay.values()) {
			java.time.LocalDate date = s.date(y);
			List<PathPoint> points = sunService.positionsOfDay(date, latitude, longitude, 15).stream()
					.filter(t -> t.position().altitudeDeg() > 0)
					.map(t -> new PathPoint(t.time().toLocalTime().toString(), round(t.position().altitudeDeg()),
							round(t.position().azimuthDeg())))
					.toList();
			list.add(new SunPath(s, s.getLabel(), date, points));
		}
		return list;
	}

	public record PathPoint(String time, double altitude, double azimuth) {
	}

	public record SunPath(SeasonDay season, String label, java.time.LocalDate date, List<PathPoint> points) {
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	public record SunResponse(LocalDateTime dateTime, double latitude, double longitude, double altitudeDeg,
			double azimuthDeg, boolean aboveHorizon, LocalDateTime sunrise, LocalDateTime solarNoon,
			LocalDateTime sunset) {
	}
}
