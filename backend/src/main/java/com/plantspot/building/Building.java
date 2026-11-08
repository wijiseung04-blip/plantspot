package com.plantspot.building;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * GIS 건물통합정보 한 동. BuildingImporter가 GeoPackage에서 채운다. [① DB 데이터 생성 프로그램]
 * 좌표(centerX, centerY, footprintWkt)는 EPSG:5186 미터 그대로 두고, 화면 표시용으로 위경도도 함께 저장한다.
 */
@Entity
@Table(name = "building", indexes = {
		@Index(name = "idx_building_dong_jibun", columnList = "dong, jibun"),
		@Index(name = "idx_building_center", columnList = "center_x, center_y") })
public class Building {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** A2 고유번호(PNU) */
	private String pnu;

	/** A4 법정동명 전체 (예: 부산광역시 사하구 하단동) */
	@Column(name = "legal_dong_name")
	private String legalDongName;

	/** 법정동명의 마지막 낱말 (예: 하단동). 검색용 */
	private String dong;

	/** A5 지번 (예: 1160-6) */
	private String jibun;

	/** A9 용도 (MySQL 예약어 USAGE를 피해 칸 이름 building_usage) */
	@Column(name = "building_usage")
	private String usage;

	/** A13 사용승인일 (원본 문자열) */
	@Column(name = "approval_date")
	private String approvalDate;

	/** A24 건물명 (비어 있는 경우 많음) */
	@Column(name = "building_name")
	private String buildingName;

	/** A25 동명 */
	@Column(name = "dong_label")
	private String dongLabel;

	/** A26 지상층수 */
	@Column(name = "ground_floors")
	private Integer groundFloors;

	/** A27 지하층수 */
	@Column(name = "basement_floors")
	private Integer basementFloors;

	/** A16 높이 원본(m) */
	@Column(name = "raw_height")
	private Double rawHeight;

	/** HeightRule로 정한 최종 높이(m) */
	private double height;

	@Enumerated(EnumType.STRING)
	@Column(name = "height_source", length = 20)
	private HeightSource heightSource;

	/** 바닥 면적(㎡) */
	private double area;

	/** 바닥 도형 중심점 (EPSG:5186) */
	@Column(name = "center_x")
	private double centerX;

	@Column(name = "center_y")
	private double centerY;

	private double latitude;

	private double longitude;

	/** 바닥 도형 (EPSG:5186, WKT MULTIPOLYGON) — 가림 각도 계산용. 큰 단지는 길어서 MEDIUMTEXT */
	@Column(name = "footprint_wkt", columnDefinition = "MEDIUMTEXT")
	private String footprintWkt;

	protected Building() {
	}

	public Long getId() {
		return id;
	}

	public String getPnu() {
		return pnu;
	}

	public String getLegalDongName() {
		return legalDongName;
	}

	public String getDong() {
		return dong;
	}

	public String getJibun() {
		return jibun;
	}

	public String getUsage() {
		return usage;
	}

	public String getApprovalDate() {
		return approvalDate;
	}

	public String getBuildingName() {
		return buildingName;
	}

	public String getDongLabel() {
		return dongLabel;
	}

	public Integer getGroundFloors() {
		return groundFloors;
	}

	public Integer getBasementFloors() {
		return basementFloors;
	}

	public Double getRawHeight() {
		return rawHeight;
	}

	public double getHeight() {
		return height;
	}

	public HeightSource getHeightSource() {
		return heightSource;
	}

	public double getArea() {
		return area;
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

	public String getFootprintWkt() {
		return footprintWkt;
	}
}
