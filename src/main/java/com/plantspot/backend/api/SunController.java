package com.plantspot.backend.api;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.sun.SolarPosition;
import com.plantspot.backend.sun.SunService;
import com.plantspot.backend.sun.SunService.SunDay;

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

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	public record SunResponse(LocalDateTime dateTime, double latitude, double longitude, double altitudeDeg,
			double azimuthDeg, boolean aboveHorizon, LocalDateTime sunrise, LocalDateTime solarNoon,
			LocalDateTime sunset) {
	}
}
