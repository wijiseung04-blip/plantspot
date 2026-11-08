package com.plantspot.plant;

import java.time.LocalDateTime;

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
 * 식물 종 정보. 농사로 원본 코드와, 추천에 쓰는 변환값(lux·직사광·창에서 거리·겨울 최저온도)을 함께 저장한다.
 * 변환값은 코드로 자동 계산하고, 관리자가 고치면 adjusted = true 로 남아 다시 가져와도 덮어쓰지 않는다.
 */
@Entity
@Table(name = "plant_species", indexes = @Index(name = "idx_plant_name", columnList = "name"))
public class PlantSpecies {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	/** 농사로 콘텐츠 번호 (직접 입력이면 null) */
	@Column(name = "cntnts_no", unique = true)
	private String cntntsNo;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "scientific_name")
	private String scientificName;

	@Column(name = "english_name")
	private String englishName;

	@Column(name = "family_name")
	private String familyName;

	@Column(name = "thumbnail_url", length = 500)
	private String thumbnailUrl;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	// ----- 농사로 원본 코드·이름 -----

	@Column(name = "light_codes")
	private String lightCodes;

	@Column(name = "light_names")
	private String lightNames;

	@Column(name = "placement_codes")
	private String placementCodes;

	@Column(name = "placement_names")
	private String placementNames;

	@Column(name = "winter_min_code")
	private String winterMinCode;

	@Column(name = "winter_min_name")
	private String winterMinName;

	@Column(name = "growth_temp_name")
	private String growthTempName;

	@Column(name = "humidity_name")
	private String humidityName;

	@Column(name = "manage_level_code")
	private String manageLevelCode;

	@Column(name = "manage_level_name")
	private String manageLevelName;

	@Column(name = "water_spring")
	private String waterSpring;

	@Column(name = "water_summer")
	private String waterSummer;

	@Column(name = "water_autumn")
	private String waterAutumn;

	@Column(name = "water_winter")
	private String waterWinter;

	@Column(columnDefinition = "TEXT")
	private String advice;

	@Column(name = "growth_info", columnDefinition = "TEXT")
	private String growthInfo;

	// ----- 변환값 (추천 기준) -----

	@Column(name = "min_lux", nullable = false)
	private int minLux;

	@Column(name = "max_lux", nullable = false)
	private int maxLux;

	/** 하루 직사광 최소(분), 0이면 없어도 됨 */
	@Column(name = "min_direct_minutes", nullable = false)
	private int minDirectMinutes;

	/** 하루 직사광 최대(분), null이면 제한 없음 */
	@Column(name = "max_direct_minutes")
	private Integer maxDirectMinutes;

	@Column(name = "min_window_distance")
	private Integer minWindowDistance;

	@Column(name = "max_window_distance")
	private Integer maxWindowDistance;

	/** 겨울 최저온도(℃) */
	@Column(name = "winter_min_temp")
	private Integer winterMinTemp;

	/** 관리자가 변환값을 직접 고쳤는가 */
	@Column(nullable = false)
	private boolean adjusted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PlantSource source;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected PlantSpecies() {
	}

	public static PlantSpecies manual(String name) {
		PlantSpecies p = new PlantSpecies();
		p.name = name;
		p.source = PlantSource.MANUAL;
		p.minLux = 300;
		p.maxLux = 10000;
		p.updatedAt = LocalDateTime.now();
		return p;
	}

	public static PlantSpecies fromNongsaro(String cntntsNo) {
		PlantSpecies p = new PlantSpecies();
		p.cntntsNo = cntntsNo;
		p.source = PlantSource.NONGSARO;
		return p;
	}

	/** 농사로 상세 값으로 채운다. 관리자가 고친 변환값은 유지한다. */
	public void applyNongsaro(NongsaroPlant d) {
		this.name = d.name();
		this.scientificName = d.scientificName();
		this.englishName = d.englishName();
		this.familyName = d.familyName();
		this.thumbnailUrl = d.thumbnailUrl();
		this.imageUrl = d.imageUrl();
		this.lightCodes = d.lightCodes();
		this.lightNames = d.lightNames();
		this.placementCodes = d.placementCodes();
		this.placementNames = d.placementNames();
		this.winterMinCode = d.winterMinCode();
		this.winterMinName = d.winterMinName();
		this.growthTempName = d.growthTempName();
		this.humidityName = d.humidityName();
		this.manageLevelCode = d.manageLevelCode();
		this.manageLevelName = d.manageLevelName();
		this.waterSpring = d.waterSpring();
		this.waterSummer = d.waterSummer();
		this.waterAutumn = d.waterAutumn();
		this.waterWinter = d.waterWinter();
		this.advice = d.advice();
		this.growthInfo = d.growthInfo();
		if (!adjusted) {
			applyConversion(PlantConverter.convert(lightCodes, placementCodes, winterMinCode));
		}
		this.updatedAt = LocalDateTime.now();
	}

	public void applyConversion(PlantConverter.Converted c) {
		this.minLux = c.minLux();
		this.maxLux = c.maxLux();
		this.minDirectMinutes = c.minDirectMinutes();
		this.maxDirectMinutes = c.maxDirectMinutes();
		this.minWindowDistance = c.minWindowDistance();
		this.maxWindowDistance = c.maxWindowDistance();
		this.winterMinTemp = c.winterMinTemp();
	}

	/** 관리자 변환표 수정 */
	public void adjust(String name, PlantConverter.Converted c) {
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
		applyConversion(c);
		this.adjusted = true;
		this.updatedAt = LocalDateTime.now();
	}

	/** 자동 변환값으로 되돌리기 */
	public void resetConversion() {
		applyConversion(PlantConverter.convert(lightCodes, placementCodes, winterMinCode));
		this.adjusted = false;
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getCntntsNo() {
		return cntntsNo;
	}

	public String getName() {
		return name;
	}

	public String getScientificName() {
		return scientificName;
	}

	public String getEnglishName() {
		return englishName;
	}

	public String getFamilyName() {
		return familyName;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public String getLightCodes() {
		return lightCodes;
	}

	public String getLightNames() {
		return lightNames;
	}

	public String getPlacementCodes() {
		return placementCodes;
	}

	public String getPlacementNames() {
		return placementNames;
	}

	public String getWinterMinCode() {
		return winterMinCode;
	}

	public String getWinterMinName() {
		return winterMinName;
	}

	public String getGrowthTempName() {
		return growthTempName;
	}

	public String getHumidityName() {
		return humidityName;
	}

	public String getManageLevelCode() {
		return manageLevelCode;
	}

	public String getManageLevelName() {
		return manageLevelName;
	}

	public String getWaterSpring() {
		return waterSpring;
	}

	public String getWaterSummer() {
		return waterSummer;
	}

	public String getWaterAutumn() {
		return waterAutumn;
	}

	public String getWaterWinter() {
		return waterWinter;
	}

	public String getAdvice() {
		return advice;
	}

	public String getGrowthInfo() {
		return growthInfo;
	}

	public int getMinLux() {
		return minLux;
	}

	public int getMaxLux() {
		return maxLux;
	}

	public int getMinDirectMinutes() {
		return minDirectMinutes;
	}

	public Integer getMaxDirectMinutes() {
		return maxDirectMinutes;
	}

	public Integer getMinWindowDistance() {
		return minWindowDistance;
	}

	public Integer getMaxWindowDistance() {
		return maxWindowDistance;
	}

	public Integer getWinterMinTemp() {
		return winterMinTemp;
	}

	public boolean isAdjusted() {
		return adjusted;
	}

	public PlantSource getSource() {
		return source;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
