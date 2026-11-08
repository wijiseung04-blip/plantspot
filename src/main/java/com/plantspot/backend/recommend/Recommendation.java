package com.plantspot.backend.recommend;

import java.time.LocalDateTime;

import com.plantspot.backend.house.Room;
import com.plantspot.backend.plant.PlantSpecies;
import com.plantspot.backend.sunlight.SeasonDay;

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

/**
 * 방의 식물 추천 결과 한 줄. 고정 자리면 season = null 한 줄, 계절 이동 자리면 계절마다 한 줄.
 * [① 분석 후 저장]
 */
@Entity
@Table(name = "recommendation", indexes = @Index(name = "idx_recommendation_room", columnList = "room_id"))
public class Recommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id")
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plant_id")
	private PlantSpecies plant;

	/** 순위 (1부터) */
	@Column(name = "rank_no", nullable = false)
	private int rank;

	@Enumerated(EnumType.STRING)
	@Column(name = "spot_type", nullable = false, length = 10)
	private PlantScorer.SpotType spotType;

	/** 계절 이동 자리일 때 계절 */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private SeasonDay season;

	/** 추천 칸 중심(평면도 cm) */
	@Column(name = "cell_x", nullable = false)
	private double cellX;

	@Column(name = "cell_y", nullable = false)
	private double cellY;

	/** 식물 전체 점수 */
	@Column(nullable = false)
	private double score;

	/** 이 칸(계절)의 점수 */
	@Column(name = "spot_score", nullable = false)
	private double spotScore;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Column(nullable = false)
	private int year;

	@Column(name = "computed_at", nullable = false)
	private LocalDateTime computedAt;

	protected Recommendation() {
	}

	public Recommendation(Room room, PlantSpecies plant, int rank, PlantScorer.SpotType spotType, SeasonDay season,
			double cellX, double cellY, double score, double spotScore, String notes, int year) {
		this.room = room;
		this.plant = plant;
		this.rank = rank;
		this.spotType = spotType;
		this.season = season;
		this.cellX = cellX;
		this.cellY = cellY;
		this.score = score;
		this.spotScore = spotScore;
		this.notes = notes;
		this.year = year;
		this.computedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Room getRoom() {
		return room;
	}

	public PlantSpecies getPlant() {
		return plant;
	}

	public int getRank() {
		return rank;
	}

	public PlantScorer.SpotType getSpotType() {
		return spotType;
	}

	public SeasonDay getSeason() {
		return season;
	}

	public double getCellX() {
		return cellX;
	}

	public double getCellY() {
		return cellY;
	}

	public double getScore() {
		return score;
	}

	public double getSpotScore() {
		return spotScore;
	}

	public String getNotes() {
		return notes;
	}

	public int getYear() {
		return year;
	}

	public LocalDateTime getComputedAt() {
		return computedAt;
	}
}
