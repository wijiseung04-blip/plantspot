package com.plantspot.backend.sunlight;

import java.time.LocalDateTime;

import com.plantspot.backend.house.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 방·계절 대표일별 25cm 칸 햇빛 계산 결과. [① 분석 후 저장] */
@Entity
@Table(name = "sunlight_cell", indexes = @Index(name = "idx_sunlight_room_season", columnList = "room_id, season, year"))
public class SunlightCell {

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

	private int ix;

	private int iy;

	/** 칸 중심 (평면도 cm) */
	private double x;

	private double y;

	@Column(name = "direct_minutes", nullable = false)
	private int directMinutes;

	/** 하늘빛 평균 조도 추정(lux) */
	@Column(nullable = false)
	private double lux;

	@Column(name = "daylight_factor", nullable = false)
	private double daylightFactor;

	/** 가장 가까운 창까지 거리(cm) */
	@Column(name = "distance_to_window", nullable = false)
	private double distanceToWindow;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private LightLevel level;

	@Column(name = "computed_at", nullable = false)
	private LocalDateTime computedAt;

	protected SunlightCell() {
	}

	public SunlightCell(Room room, SeasonDay season, int year, SunlightCalculator.Cell c) {
		this.room = room;
		this.season = season;
		this.year = year;
		this.ix = c.ix();
		this.iy = c.iy();
		this.x = c.x();
		this.y = c.y();
		this.directMinutes = c.directMinutes();
		this.lux = c.lux();
		this.daylightFactor = c.daylightFactor();
		this.distanceToWindow = c.distanceToWindow();
		this.level = c.level();
		this.computedAt = LocalDateTime.now();
	}

	public SunlightCalculator.Cell toCell() {
		return new SunlightCalculator.Cell(ix, iy, x, y, directMinutes, lux, daylightFactor, distanceToWindow, level);
	}

	public Long getId() {
		return id;
	}

	public Room getRoom() {
		return room;
	}

	public SeasonDay getSeason() {
		return season;
	}

	public int getYear() {
		return year;
	}
}
