package com.springboot.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 지형(땅 높이) 격자 정보. [① 공개 데이터 → DB 전환: 고도 API를 격자 점마다 호출해 terrain_point 에 저장]
 * 격자 점 (ix, iy)의 좌표 = (originX + ix × spacing, originY + iy × spacing), EPSG:5186 미터.
 */
@Entity
@Table(name = "terrain_grid")
public class TerrainGrid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)
	private Long id;

	@Column(name = "origin_x", nullable = false)
	private double originX;

	@Column(name = "origin_y", nullable = false)
	private double originY;

	/** 격자 간격(m) */
	@Column(nullable = false)
	private double spacing;

	@Column(nullable = false)
	private int nx;

	@Column(nullable = false)
	private int ny;

	/** 자료 출처 (예: Open-Meteo Copernicus DEM GLO-90) */
	private String source;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** 모든 점을 받은 시각 (중간에 멈췄으면 null → 다시 실행하면 이어서 받는다) */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected TerrainGrid() {
	}

	public TerrainGrid(double originX, double originY, double spacing, int nx, int ny, String source) {
		this.originX = originX;
		this.originY = originY;
		this.spacing = spacing;
		this.nx = nx;
		this.ny = ny;
		this.source = source;
		this.createdAt = LocalDateTime.now();
	}

	public boolean sameShape(double originX, double originY, double spacing, int nx, int ny) {
		return this.originX == originX && this.originY == originY && this.spacing == spacing && this.nx == nx
				&& this.ny == ny;
	}

	public void complete() {
		this.completedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public double getOriginX() {
		return originX;
	}

	public double getOriginY() {
		return originY;
	}

	public double getSpacing() {
		return spacing;
	}

	public int getNx() {
		return nx;
	}

	public int getNy() {
		return ny;
	}

	public String getSource() {
		return source;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}
}
