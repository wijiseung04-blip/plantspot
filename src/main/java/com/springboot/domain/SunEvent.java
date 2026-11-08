package com.springboot.domain;

import com.springboot.dto.KasiRiseSet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한국천문연구원 출몰시각 (조회 결과 저장). 위경도는 소수 둘째 자리로 묶어 저장한다(약 1km).
 * 시민박명 시작 전·종료 후의 밝아짐은 인공조명으로 보고 센서 분석에서 뺀다.
 */
@Entity
@Table(name = "sun_event", uniqueConstraints = @UniqueConstraint(columnNames = { "locdate", "lat_key", "lon_key" }))
public class SunEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@Column(nullable = false)
	private LocalDate locdate;

	@Column(name = "lat_key", nullable = false)
	private double latKey;

	@Column(name = "lon_key", nullable = false)
	private double lonKey;

	private LocalTime sunrise;

	@Column(name = "solar_noon")
	private LocalTime solarNoon;

	private LocalTime sunset;

	@Column(name = "civil_dawn")
	private LocalTime civilDawn;

	@Column(name = "civil_dusk")
	private LocalTime civilDusk;

	@Column(name = "nautical_dawn")
	private LocalTime nauticalDawn;

	@Column(name = "nautical_dusk")
	private LocalTime nauticalDusk;

	@Column(name = "astro_dawn")
	private LocalTime astroDawn;

	@Column(name = "astro_dusk")
	private LocalTime astroDusk;

	@Column(name = "fetched_at", nullable = false)
	private LocalDateTime fetchedAt;

	protected SunEvent() {
	}

	public SunEvent(LocalDate locdate, double latKey, double lonKey, KasiRiseSet r) {
		this.locdate = locdate;
		this.latKey = latKey;
		this.lonKey = lonKey;
		this.sunrise = r.sunrise();
		this.solarNoon = r.solarNoon();
		this.sunset = r.sunset();
		this.civilDawn = r.civilDawn();
		this.civilDusk = r.civilDusk();
		this.nauticalDawn = r.nauticalDawn();
		this.nauticalDusk = r.nauticalDusk();
		this.astroDawn = r.astroDawn();
		this.astroDusk = r.astroDusk();
		this.fetchedAt = LocalDateTime.now();
	}

	public static double key(double degree) {
		return Math.round(degree * 100) / 100.0;
	}

	/** 시민박명 사이(자연광이 있는 시간)인가. */
	public boolean isNaturalLight(LocalTime t) {
		return civilDawn != null && civilDusk != null && !t.isBefore(civilDawn) && !t.isAfter(civilDusk);
	}

	public Long getId() {
		return id;
	}

	public LocalDate getLocdate() {
		return locdate;
	}

	public double getLatKey() {
		return latKey;
	}

	public double getLonKey() {
		return lonKey;
	}

	public LocalTime getSunrise() {
		return sunrise;
	}

	public LocalTime getSolarNoon() {
		return solarNoon;
	}

	public LocalTime getSunset() {
		return sunset;
	}

	public LocalTime getCivilDawn() {
		return civilDawn;
	}

	public LocalTime getCivilDusk() {
		return civilDusk;
	}

	public LocalTime getNauticalDawn() {
		return nauticalDawn;
	}

	public LocalTime getNauticalDusk() {
		return nauticalDusk;
	}

	public LocalTime getAstroDawn() {
		return astroDawn;
	}

	public LocalTime getAstroDusk() {
		return astroDusk;
	}

	public LocalDateTime getFetchedAt() {
		return fetchedAt;
	}
}
