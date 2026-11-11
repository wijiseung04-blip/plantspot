package com.springboot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 지형 격자 점 하나의 땅 높이(해발 m). */
@Entity
@Table(name = "terrain_point", uniqueConstraints = @UniqueConstraint(columnNames = { "grid_id", "ix", "iy" }))
public class TerrainPoint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grid_id")
	private TerrainGrid grid;

	@Column(nullable = false)
	private int ix;

	@Column(nullable = false)
	private int iy;

	/** 해발 높이(m) */
	@Column(nullable = false)
	private double elevation;

	protected TerrainPoint() {
	}

	public TerrainPoint(TerrainGrid grid, int ix, int iy, double elevation) {
		this.grid = grid;
		this.ix = ix;
		this.iy = iy;
		this.elevation = elevation;
	}

	public int getIx() {
		return ix;
	}

	public int getIy() {
		return iy;
	}

	public double getElevation() {
		return elevation;
	}
}
