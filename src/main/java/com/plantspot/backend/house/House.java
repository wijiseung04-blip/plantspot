package com.plantspot.backend.house;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.plantspot.backend.building.Building;
import com.plantspot.backend.member.Member;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** 회원의 집. 동·지번으로 building 테이블의 건물과 연결하고 그 중심점을 위치로 쓴다. */
@Entity
@Table(name = "house")
public class House {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "building_id")
	private Building building;

	@Column(nullable = false, length = 40)
	private String name;

	@Column(nullable = false)
	private String dong;

	@Column(nullable = false)
	private String jibun;

	/** 우리 집이 있는 층 (1층부터) */
	@Column(nullable = false)
	private int floor;

	/** 층 높이(m). 창문 높이 = (층-1) × 층 높이 + 창턱 높이 */
	@Column(name = "floor_height", nullable = false)
	private double floorHeight;

	@Column(name = "center_x", nullable = false)
	private double centerX;

	@Column(name = "center_y", nullable = false)
	private double centerY;

	private double latitude;

	private double longitude;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "house", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<Room> rooms = new ArrayList<>();

	protected House() {
	}

	public House(Member member, Building building, String name, int floor, double floorHeight) {
		this.member = member;
		this.building = building;
		this.name = name;
		this.dong = building.getDong();
		this.jibun = building.getJibun();
		this.floor = floor;
		this.floorHeight = floorHeight;
		this.centerX = building.getCenterX();
		this.centerY = building.getCenterY();
		this.latitude = building.getLatitude();
		this.longitude = building.getLongitude();
		this.createdAt = LocalDateTime.now();
	}

	public void update(String name, int floor, double floorHeight) {
		this.name = name;
		this.floor = floor;
		this.floorHeight = floorHeight;
	}

	public boolean isOwnedBy(Long memberId) {
		return member.getId().equals(memberId);
	}

	/** 지면에서 우리 층 바닥까지 높이(m). */
	public double floorElevation() {
		return (floor - 1) * floorHeight;
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public Building getBuilding() {
		return building;
	}

	public String getName() {
		return name;
	}

	public String getDong() {
		return dong;
	}

	public String getJibun() {
		return jibun;
	}

	public int getFloor() {
		return floor;
	}

	public double getFloorHeight() {
		return floorHeight;
	}

	public double getCenterX() {
		return centerX;
	}

	public double getCenterY() {
		return centerY;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public List<Room> getRooms() {
		return rooms;
	}
}
