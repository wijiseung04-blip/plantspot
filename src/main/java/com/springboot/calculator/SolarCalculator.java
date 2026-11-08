package com.springboot.calculator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * NOAA 태양 위치 공식(Solar Calculator 스프레드시트, Meeus 근사)으로 해의 고도·방위각과 일출·남중·일몰을 계산한다.
 * 정확도는 고도·방위각 약 0.01°, 출몰 시각 약 1분 수준이며 1901~2099년 범위에서 쓴다.
 * 평면도 햇빛 에디터(브라우저)도 같은 공식을 쓴다.
 */
@Component
public class SolarCalculator {

	/** 일출·일몰 기준 천정거리: 90° + 대기 굴절 34′ + 해 반지름 16′. */
	private static final double SUNRISE_ZENITH_DEG = 90.833;

	private static final double MINUTES_PER_DAY = 1440.0;

	/** 특정 순간, 특정 위치(위도·경도, 도)의 해 위치. 경도는 동경이 양수. */
	public SolarPosition position(Instant instant, double latitudeDeg, double longitudeDeg) {
		double jd = julianDay(instant);
		SunTerms s = sunTerms(jd);

		double utcMinutes = minutesOfUtcDay(instant);
		double trueSolarMinutes = mod(utcMinutes + s.equationOfTimeMin + 4.0 * longitudeDeg, MINUTES_PER_DAY);
		double hourAngleDeg = trueSolarMinutes / 4.0 - 180.0;

		double lat = Math.toRadians(latitudeDeg);
		double decl = Math.toRadians(s.declinationDeg);
		double ha = Math.toRadians(hourAngleDeg);

		double cosZenith = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(ha);
		double zenith = Math.acos(clamp(cosZenith));
		double altitudeDeg = 90.0 - Math.toDegrees(zenith);

		// 남 기준 방위각을 구한 뒤 북 0° 기준으로 바꾼다.
		double azimuthFromSouth = Math.atan2(Math.sin(ha),
				Math.cos(ha) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat));
		double azimuthDeg = mod(Math.toDegrees(azimuthFromSouth) + 180.0, 360.0);

		return new SolarPosition(altitudeDeg, azimuthDeg);
	}

	/** 남중 시각(해가 정남에 오는 시각, 고도가 가장 높음). */
	public ZonedDateTime solarNoon(LocalDate date, double latitudeDeg, double longitudeDeg, ZoneId zone) {
		// 근사값으로 시작해 그 시각의 균시차로 한 번 더 고친다.
		double noonUtcMin = 720.0 - 4.0 * longitudeDeg;
		for (int i = 0; i < 2; i++) {
			SunTerms s = sunTerms(julianDay(utcInstant(date, noonUtcMin)));
			noonUtcMin = 720.0 - 4.0 * longitudeDeg - s.equationOfTimeMin;
		}
		return utcInstant(date, noonUtcMin).atZone(zone);
	}

	/** 일출 시각. 백야·극야로 해가 뜨지 않으면 비어 있다. */
	public Optional<ZonedDateTime> sunrise(LocalDate date, double latitudeDeg, double longitudeDeg, ZoneId zone) {
		return riseOrSet(date, latitudeDeg, longitudeDeg, zone, true);
	}

	/** 일몰 시각. 백야·극야로 해가 지지 않으면 비어 있다. */
	public Optional<ZonedDateTime> sunset(LocalDate date, double latitudeDeg, double longitudeDeg, ZoneId zone) {
		return riseOrSet(date, latitudeDeg, longitudeDeg, zone, false);
	}

	private Optional<ZonedDateTime> riseOrSet(LocalDate date, double latitudeDeg, double longitudeDeg, ZoneId zone,
			boolean rise) {
		// date의 UTC 0시 기준 분. 동경 지역의 일출처럼 음수(전날 UTC)가 나와도 순간 자체는 맞다.
		double minutes = 720.0 - 4.0 * longitudeDeg;
		double lat = Math.toRadians(latitudeDeg);
		for (int i = 0; i < 3; i++) {
			SunTerms s = sunTerms(julianDay(utcInstant(date, minutes)));
			double decl = Math.toRadians(s.declinationDeg);
			double cosH = Math.cos(Math.toRadians(SUNRISE_ZENITH_DEG)) / (Math.cos(lat) * Math.cos(decl))
					- Math.tan(lat) * Math.tan(decl);
			if (cosH < -1 || cosH > 1) {
				return Optional.empty();
			}
			double hourAngleDeg = Math.toDegrees(Math.acos(cosH)) * (rise ? 1 : -1);
			minutes = 720.0 - 4.0 * (longitudeDeg + hourAngleDeg) - s.equationOfTimeMin;
		}
		return Optional.of(utcInstant(date, minutes).atZone(zone));
	}

	// ----- NOAA 공식 -----

	/** 균시차(분)와 적위(도). */
	private record SunTerms(double equationOfTimeMin, double declinationDeg) {
	}

	private SunTerms sunTerms(double julianDay) {
		double t = (julianDay - 2451545.0) / 36525.0; // 율리우스 세기

		double meanLong = mod(280.46646 + t * (36000.76983 + t * 0.0003032), 360.0);
		double meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t);
		double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);

		double m = Math.toRadians(meanAnomaly);
		double center = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
				+ Math.sin(2 * m) * (0.019993 - 0.000101 * t)
				+ Math.sin(3 * m) * 0.000289;
		double trueLong = meanLong + center;

		double omega = Math.toRadians(125.04 - 1934.136 * t);
		double apparentLong = trueLong - 0.00569 - 0.00478 * Math.sin(omega);

		double meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
		double obliquity = meanObliquity + 0.00256 * Math.cos(omega);

		double eps = Math.toRadians(obliquity);
		double declination = Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(Math.toRadians(apparentLong))));

		double y = Math.pow(Math.tan(eps / 2), 2);
		double l0 = Math.toRadians(meanLong);
		double eot = y * Math.sin(2 * l0)
				- 2 * eccentricity * Math.sin(m)
				+ 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0)
				- 0.5 * y * y * Math.sin(4 * l0)
				- 1.25 * eccentricity * eccentricity * Math.sin(2 * m);
		double equationOfTimeMin = 4.0 * Math.toDegrees(eot);

		return new SunTerms(equationOfTimeMin, declination);
	}

	private static double julianDay(Instant instant) {
		return instant.getEpochSecond() / 86400.0 + instant.getNano() / 86_400_000_000_000.0 + 2440587.5;
	}

	private static double minutesOfUtcDay(Instant instant) {
		long secondsOfDay = Math.floorMod(instant.getEpochSecond(), 86400L);
		return secondsOfDay / 60.0 + instant.getNano() / 60_000_000_000.0;
	}

	private static Instant utcInstant(LocalDate utcDate, double minutes) {
		long epochSecond = utcDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond() + Math.round(minutes * 60.0);
		return Instant.ofEpochSecond(epochSecond);
	}

	private static double mod(double value, double modulus) {
		double r = value % modulus;
		return r < 0 ? r + modulus : r;
	}

	private static double clamp(double v) {
		return Math.max(-1.0, Math.min(1.0, v));
	}
}
