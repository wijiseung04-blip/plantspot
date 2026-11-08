package com.plantspot.weather;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.sun.SolarCalculator;
import com.plantspot.sun.SunService;

/**
 * 출몰시각·ASOS 시간자료를 받아 DB에 저장하고(같은 날짜는 다시 부르지 않음), 센서 분석용 판단을 제공한다.
 * [① 인터넷 공개 데이터 → DB]
 */
@Service
@Transactional(readOnly = true)
public class PublicDataService {

	private static final Logger log = LoggerFactory.getLogger(PublicDataService.class);

	private final PublicDataApi api;
	private final SunEventRepository sunEvents;
	private final WeatherHourRepository weatherHours;
	private final SolarCalculator solar;
	private final int defaultStation;

	public PublicDataService(PublicDataApi api, SunEventRepository sunEvents, WeatherHourRepository weatherHours,
			SolarCalculator solar, @Value("${api.kma.station-id:159}") int defaultStation) {
		this.api = api;
		this.sunEvents = sunEvents;
		this.weatherHours = weatherHours;
		this.solar = solar;
		this.defaultStation = defaultStation;
	}

	public int defaultStation() {
		return defaultStation;
	}

	// ----- 출몰시각 -----

	/** 저장된 값, 없으면 API로 받아 저장. 키가 없으면 비어 있음. */
	@Transactional
	public Optional<SunEvent> sunEvent(LocalDate date, double lat, double lon) {
		double latKey = SunEvent.key(lat);
		double lonKey = SunEvent.key(lon);
		Optional<SunEvent> stored = sunEvents.findByLocdateAndLatKeyAndLonKey(date, latKey, lonKey);
		if (stored.isPresent() || !api.hasKasiKey()) {
			return stored;
		}
		KasiRiseSet r = api.riseSet(date, latKey, lonKey);
		log.info("출몰시각 저장: {} ({}, {})", date, latKey, lonKey);
		return Optional.of(sunEvents.save(new SunEvent(date, latKey, lonKey, r)));
	}

	/** 우리 해 위치 계산과 천문연 값 비교 (분 단위 차이). */
	@Transactional
	public SunCompare compare(LocalDate date, double lat, double lon) {
		ZonedDateTime rise = solar.sunrise(date, lat, lon, SunService.SEOUL).orElse(null);
		ZonedDateTime noon = solar.solarNoon(date, lat, lon, SunService.SEOUL);
		ZonedDateTime set = solar.sunset(date, lat, lon, SunService.SEOUL).orElse(null);
		Optional<SunEvent> kasi = sunEvent(date, lat, lon);
		LocalTime calcRise = rise == null ? null : rise.toLocalTime().withNano(0);
		LocalTime calcSet = set == null ? null : set.toLocalTime().withNano(0);
		LocalTime calcNoon = noon.toLocalTime().withNano(0);
		return new SunCompare(date, calcRise, calcNoon, calcSet,
				kasi.map(SunEvent::getSunrise).orElse(null), kasi.map(SunEvent::getSolarNoon).orElse(null),
				kasi.map(SunEvent::getSunset).orElse(null),
				diff(calcRise, kasi.map(SunEvent::getSunrise).orElse(null)),
				diff(calcNoon, kasi.map(SunEvent::getSolarNoon).orElse(null)),
				diff(calcSet, kasi.map(SunEvent::getSunset).orElse(null)),
				kasi.map(SunEvent::getCivilDawn).orElse(null), kasi.map(SunEvent::getCivilDusk).orElse(null),
				kasi.isPresent());
	}

	private static Double diff(LocalTime calc, LocalTime kasi) {
		if (calc == null || kasi == null) {
			return null;
		}
		// 천문연 값은 분 단위(초 버림)이므로 계산값도 분 단위로 비교
		return (double) Duration.between(kasi, calc.withSecond(0)).toMinutes();
	}

	/** 그 시각이 자연광 시간(시민박명 사이)인가. 출몰시각을 모르면 계산한 일출 30분 전 ~ 일몰 30분 후. */
	@Transactional
	public boolean isNaturalLight(LocalDateTime t, double lat, double lon) {
		Optional<SunEvent> e = sunEvent(t.toLocalDate(), lat, lon);
		if (e.isPresent() && e.get().getCivilDawn() != null) {
			return e.get().isNaturalLight(t.toLocalTime());
		}
		var rise = solar.sunrise(t.toLocalDate(), lat, lon, SunService.SEOUL);
		var set = solar.sunset(t.toLocalDate(), lat, lon, SunService.SEOUL);
		if (rise.isEmpty() || set.isEmpty()) {
			return false;
		}
		LocalTime time = t.toLocalTime();
		return !time.isBefore(rise.get().toLocalTime().minusMinutes(30))
				&& !time.isAfter(set.get().toLocalTime().plusMinutes(30));
	}

	// ----- ASOS -----

	/** from~to 날짜의 ASOS 시간자료를 받아 저장 (이미 24시간 있는 날은 건너뜀, 오늘 이후는 제외). */
	@Transactional
	public CollectResult collectWeather(LocalDate from, LocalDate to, int stationId) {
		LocalDate yesterday = LocalDate.now(SunService.SEOUL).minusDays(1);
		LocalDate end = to.isAfter(yesterday) ? yesterday : to;
		int days = 0;
		int saved = 0;
		int skipped = 0;
		for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
			List<WeatherHour> existing = hours(d, stationId);
			if (existing.size() >= 24) {
				skipped++;
				continue;
			}
			for (WeatherHour h : api.asosDay(d, stationId).hours()) {
				if (weatherHours.findByStationIdAndObservedAt(h.getStationId(), h.getObservedAt()).isEmpty()) {
					weatherHours.save(h);
					saved++;
				}
			}
			days++;
		}
		log.info("ASOS 저장: {}~{} 지점 {} — {}일 조회, {}시간 저장, {}일 건너뜀", from, end, stationId, days, saved, skipped);
		return new CollectResult(from, end, stationId, days, saved, skipped);
	}

	/** 그 날 0시~23시 자료 */
	public List<WeatherHour> hours(LocalDate date, int stationId) {
		return weatherHours.findByStationIdAndObservedAtBetweenOrderByObservedAtAsc(stationId, date.atStartOfDay(),
				date.atTime(23, 59));
	}

	/** 그 시각(정시 기준 1시간)이 맑았는가. 자료가 없으면 비어 있음. */
	public Optional<Boolean> isClear(LocalDateTime t, int stationId) {
		LocalDateTime hourEnd = t.withMinute(0).withSecond(0).withNano(0).plusHours(t.getMinute() == 0 ? 0 : 1);
		return weatherHours.findByStationIdAndObservedAt(stationId, hourEnd).map(WeatherHour::isClear);
	}

	/** 매일 오전 10시 47분: 어제 ASOS 자료 자동 저장 (키가 있을 때만). */
	@Scheduled(cron = "0 47 10 * * *", zone = "Asia/Seoul")
	@Transactional
	public void collectYesterday() {
		if (!api.hasKmaKey()) {
			return;
		}
		LocalDate y = LocalDate.now(SunService.SEOUL).minusDays(1);
		try {
			collectWeather(y.minusDays(2), y, defaultStation);
		} catch (RuntimeException e) {
			log.warn("ASOS 자동 저장 실패: {}", e.getMessage());
		}
	}

	public Status status() {
		return new Status(api.hasKasiKey(), api.hasKmaKey(), defaultStation, sunEvents.count(),
				weatherHours.countByStationId(defaultStation));
	}

	// ----- 응답 -----

	public record SunCompare(LocalDate date, LocalTime calcSunrise, LocalTime calcSolarNoon, LocalTime calcSunset,
			LocalTime kasiSunrise, LocalTime kasiSolarNoon, LocalTime kasiSunset, Double sunriseDiffMin,
			Double solarNoonDiffMin, Double sunsetDiffMin, LocalTime civilDawn, LocalTime civilDusk,
			boolean kasiAvailable) {
	}

	public record CollectResult(LocalDate from, LocalDate to, int stationId, int daysFetched, int hoursSaved,
			int daysSkipped) {
	}

	public record Status(boolean kasiKey, boolean kmaKey, int stationId, long sunEvents, long weatherHours) {
	}
}
