package com.plantspot.house;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 방. 모양은 평면도 좌표(cm) 다각형으로 저장한다.
 * orientationDeg = 평면도 위쪽이 가리키는 방위각 (0이면 위쪽이 북쪽).
 */
@Entity
@Table(name = "room")
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "house_id")
	private House house;

	@Column(nullable = false, length = 40)
	private String name;

	/** "x,y;x,y;..." (cm) */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String shape;

	@Column(name = "orientation_deg", nullable = false)
	private double orientationDeg;

	/** 천장 높이(cm) */
	@Column(name = "ceiling_height", nullable = false)
	private double ceilingHeight;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CurtainType curtain;

	/** 겨울 최저 실내 온도(℃): 사용자가 입력하거나 온습도 센서로 측정. 식물 겨울 최저온도와 비교해 경고 */
	@Column(name = "winter_min_temp")
	private Double winterMinTemp;

	/** 평면도 이미지(업로드 파일) 번호, 없으면 null */
	@Column(name = "floor_plan_file_id")
	private Long floorPlanFileId;

	@OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<RoomWindow> windows = new ArrayList<>();

	protected Room() {
	}

	public Room(House house, String name, List<Point> shape, double orientationDeg, double ceilingHeight,
			CurtainType curtain) {
		this.house = house;
		update(name, shape, orientationDeg, ceilingHeight, curtain);
	}

	public void update(String name, List<Point> shape, double orientationDeg, double ceilingHeight,
			CurtainType curtain) {
		this.name = name;
		this.shape = Shape.encode(shape);
		this.orientationDeg = Shape.normalize(orientationDeg);
		this.ceilingHeight = ceilingHeight;
		this.curtain = curtain;
	}

	public void changeWinterMinTemp(Double celsius) {
		this.winterMinTemp = celsius;
	}

	public Double getWinterMinTemp() {
		return winterMinTemp;
	}

	public void attachFloorPlan(Long fileId) {
		this.floorPlanFileId = fileId;
	}

	public List<Point> points() {
		return Shape.decode(shape);
	}

	public Long getId() {
		return id;
	}

	public House getHouse() {
		return house;
	}

	public String getName() {
		return name;
	}

	public double getOrientationDeg() {
		return orientationDeg;
	}

	public double getCeilingHeight() {
		return ceilingHeight;
	}

	public CurtainType getCurtain() {
		return curtain;
	}

	public Long getFloorPlanFileId() {
		return floorPlanFileId;
	}

	public List<RoomWindow> getWindows() {
		return windows;
	}
}
