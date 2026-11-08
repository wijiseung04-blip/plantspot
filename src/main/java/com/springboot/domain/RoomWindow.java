package com.springboot.domain;

import com.springboot.calculator.Shape;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 창문. 방 다각형의 wallIndex번 벽(점 wallIndex → 다음 점)에 붙는다. 길이 단위는 cm.
 * 테이블 이름은 SQL 예약어(WINDOW)를 피해 room_window.
 */
@Entity
@Table(name = "room_window")
public class RoomWindow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id")
	private Room room;

	@Column(name = "wall_index", nullable = false)
	private int wallIndex;

	/** 벽 시작점에서 창 왼쪽 끝까지 거리 */
	@Column(name = "offset_cm", nullable = false)
	private double offset;

	@Column(nullable = false)
	private double width;

	@Column(nullable = false)
	private double height;

	/** 바닥에서 창 아래쪽까지 높이 */
	@Column(name = "sill_height", nullable = false)
	private double sillHeight;

	/** 창이 바라보는 방위각 (벽 방향과 방 orientation으로 계산해 저장) */
	@Column(name = "azimuth_deg", nullable = false)
	private double azimuthDeg;

	protected RoomWindow() {
	}

	public RoomWindow(Room room, int wallIndex, double offset, double width, double height, double sillHeight) {
		this.room = room;
		update(wallIndex, offset, width, height, sillHeight);
	}

	public void update(int wallIndex, double offset, double width, double height, double sillHeight) {
		this.wallIndex = wallIndex;
		this.offset = offset;
		this.width = width;
		this.height = height;
		this.sillHeight = sillHeight;
		recalculateAzimuth();
	}

	/** 방 모양·방향이 바뀌면 다시 계산 */
	public void recalculateAzimuth() {
		this.azimuthDeg = Shape.wallAzimuth(room.points(), wallIndex, room.getOrientationDeg());
	}

	public Long getId() {
		return id;
	}

	public Room getRoom() {
		return room;
	}

	public int getWallIndex() {
		return wallIndex;
	}

	public double getOffset() {
		return offset;
	}

	public double getWidth() {
		return width;
	}

	public double getHeight() {
		return height;
	}

	public double getSillHeight() {
		return sillHeight;
	}

	public double getAzimuthDeg() {
		return azimuthDeg;
	}
}
