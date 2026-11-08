package com.springboot.domain;

import java.time.LocalDateTime;


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
import jakarta.persistence.UniqueConstraint;

/** 집에서 본 방향 칸(5°)별 가림 각도. [① 분석 후 저장] */
@Entity
@Table(name = "obstruction_angle",
		uniqueConstraints = @UniqueConstraint(columnNames = { "house_id", "azimuth_bin", "source" }),
		indexes = @Index(name = "idx_obstruction_house", columnList = "house_id"))
public class ObstructionAngle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "house_id")
	private House house;

	/** 칸 시작 방위각 (0, 5, ..., 355) */
	@Column(name = "azimuth_bin", nullable = false)
	private int azimuthBin;

	@Column(name = "angle_deg", nullable = false)
	private double angleDeg;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ObstructionSource source;

	/** 건물 데이터일 때 원인 건물 id */
	@Column(name = "building_id")
	private Long buildingId;

	/** 원인까지 수평 거리(m) */
	private Double distance;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected ObstructionAngle() {
	}

	public ObstructionAngle(House house, int azimuthBin, double angleDeg, ObstructionSource source, Long buildingId,
			Double distance) {
		this.house = house;
		this.azimuthBin = azimuthBin;
		this.angleDeg = angleDeg;
		this.source = source;
		this.buildingId = buildingId;
		this.distance = distance;
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public House getHouse() {
		return house;
	}

	public int getAzimuthBin() {
		return azimuthBin;
	}

	public double getAngleDeg() {
		return angleDeg;
	}

	public ObstructionSource getSource() {
		return source;
	}

	public Long getBuildingId() {
		return buildingId;
	}

	public Double getDistance() {
		return distance;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
