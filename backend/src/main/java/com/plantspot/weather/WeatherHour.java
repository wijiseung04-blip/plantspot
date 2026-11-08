package com.plantspot.weather;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 기상청 ASOS 시간자료 (조회 결과 저장). 전일(D-1)까지만 제공된다.
 * 일조(ss)가 0에 가까운 시간은 흐림으로 보고 센서 분석에서 뺀다.
 */
@Entity
@Table(name = "weather_hour", uniqueConstraints = @UniqueConstraint(columnNames = { "station_id", "observed_at" }),
		indexes = @Index(name = "idx_weather_time", columnList = "observed_at"))
public class WeatherHour {

	/** 이 시간 동안 일조가 이만큼(시간) 이상이면 맑음 */
	public static final double CLEAR_SUNSHINE_HOURS = 0.5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@Column(name = "station_id", nullable = false)
	private int stationId;

	@Column(name = "station_name")
	private String stationName;

	/** 관측 시각 (그 시각까지 1시간 값) */
	@Column(name = "observed_at", nullable = false)
	private LocalDateTime observedAt;

	/** 기온(℃) */
	private Double temperature;

	/** 습도(%) */
	private Double humidity;

	/** 일조(시간, 0~1) */
	@Column(name = "sunshine_hours")
	private Double sunshineHours;

	/** 일사(MJ/m²) */
	private Double insolation;

	/** 전운량(0~10) */
	@Column(name = "cloud_cover")
	private Double cloudCover;

	/** 일조 품질검사 플래그 (0 정상, 그 외 의심) */
	@Column(name = "sunshine_qc")
	private String sunshineQc;

	protected WeatherHour() {
	}

	public WeatherHour(int stationId, String stationName, LocalDateTime observedAt, Double temperature,
			Double humidity, Double sunshineHours, Double insolation, Double cloudCover, String sunshineQc) {
		this.stationId = stationId;
		this.stationName = stationName;
		this.observedAt = observedAt;
		this.temperature = temperature;
		this.humidity = humidity;
		this.sunshineHours = sunshineHours;
		this.insolation = insolation;
		this.cloudCover = cloudCover;
		this.sunshineQc = sunshineQc;
	}

	/** 맑은 시간(분석에 쓸 수 있는 시간)인가. 품질 의심 값은 쓰지 않는다. */
	public boolean isClear() {
		boolean qcOk = sunshineQc == null || sunshineQc.isBlank() || "0".equals(sunshineQc.trim());
		return qcOk && sunshineHours != null && sunshineHours >= CLEAR_SUNSHINE_HOURS;
	}

	public Long getId() {
		return id;
	}

	public int getStationId() {
		return stationId;
	}

	public String getStationName() {
		return stationName;
	}

	public LocalDateTime getObservedAt() {
		return observedAt;
	}

	public Double getTemperature() {
		return temperature;
	}

	public Double getHumidity() {
		return humidity;
	}

	public Double getSunshineHours() {
		return sunshineHours;
	}

	public Double getInsolation() {
		return insolation;
	}

	public Double getCloudCover() {
		return cloudCover;
	}

	public String getSunshineQc() {
		return sunshineQc;
	}
}
