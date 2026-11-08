package com.springboot.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 방·계절 대표일 계산의 요약 (낮 길이, 실외 확산광, 창별 직사광 시간, 보정 여부). */
@Entity
@Table(name = "sunlight_summary", uniqueConstraints = @UniqueConstraint(columnNames = { "room_id", "season", "year" }))
public class SunlightSummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id")
	private Room room;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SeasonDay season;

	@Column(nullable = false)
	private int year;

	@Column(nullable = false)
	private LocalDate date;

	@Column(name = "daylight_minutes", nullable = false)
	private int daylightMinutes;

	@Column(name = "outdoor_lux", nullable = false)
	private double outdoorLux;

	/** "창id:분,창id:분" */
	@Column(name = "window_minutes", nullable = false)
	private String windowMinutes;

	/** 센서 실측 가림 각도를 반영했는가 */
	@Column(nullable = false)
	private boolean calibrated;

	@Column(name = "computed_at", nullable = false)
	private LocalDateTime computedAt;

	protected SunlightSummary() {
	}

	public SunlightSummary(Room room, SeasonDay season, int year, LocalDate date, int daylightMinutes,
			double outdoorLux, String windowMinutes, boolean calibrated) {
		this.room = room;
		this.season = season;
		this.year = year;
		this.date = date;
		this.daylightMinutes = daylightMinutes;
		this.outdoorLux = outdoorLux;
		this.windowMinutes = windowMinutes;
		this.calibrated = calibrated;
		this.computedAt = LocalDateTime.now();
	}

	public SeasonDay getSeason() {
		return season;
	}

	public int getYear() {
		return year;
	}

	public LocalDate getDate() {
		return date;
	}

	public int getDaylightMinutes() {
		return daylightMinutes;
	}

	public double getOutdoorLux() {
		return outdoorLux;
	}

	public String getWindowMinutes() {
		return windowMinutes;
	}

	public boolean isCalibrated() {
		return calibrated;
	}

	public LocalDateTime getComputedAt() {
		return computedAt;
	}
}
