package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/** 기준값은 CLAUDE.md 4장(파이썬 검증값). 부산 35.10N, 128.97E. */
class SolarCalculatorTest {

	private static final double LAT = 35.10;
	private static final double LON = 128.97;
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SolarCalculator calc = new SolarCalculator();

	private SolarPosition atNoon(LocalDate date) {
		ZonedDateTime noon = calc.solarNoon(date, LAT, LON, SEOUL);
		return calc.position(noon.toInstant(), LAT, LON);
	}

	@Test
	void 춘분_정오_고도는_약_54_7도() {
		SolarPosition p = atNoon(LocalDate.of(2026, 3, 20));
		assertThat(p.altitudeDeg()).isCloseTo(54.7, within(0.3));
		assertThat(p.azimuthDeg()).isCloseTo(180.0, within(0.5));
	}

	@Test
	void 하지_정오_고도는_약_78_3도() {
		SolarPosition p = atNoon(LocalDate.of(2026, 6, 21));
		assertThat(p.altitudeDeg()).isCloseTo(78.3, within(0.2));
		assertThat(p.azimuthDeg()).isCloseTo(180.0, within(1.0));
	}

	@Test
	void 동지_정오_고도는_약_31_5도() {
		SolarPosition p = atNoon(LocalDate.of(2026, 12, 21));
		assertThat(p.altitudeDeg()).isCloseTo(31.5, within(0.2));
		assertThat(p.azimuthDeg()).isCloseTo(180.0, within(0.5));
	}

	@Test
	void 남중_고도가_하루_중_가장_높다() {
		LocalDate date = LocalDate.of(2026, 12, 21);
		ZonedDateTime noon = calc.solarNoon(date, LAT, LON, SEOUL);
		double noonAlt = calc.position(noon.toInstant(), LAT, LON).altitudeDeg();
		for (int min = -120; min <= 120; min += 15) {
			if (min == 0) {
				continue;
			}
			double alt = calc.position(noon.plusMinutes(min).toInstant(), LAT, LON).altitudeDeg();
			assertThat(alt).isLessThan(noonAlt);
		}
	}

	@Test
	void 오전에는_동쪽_오후에는_서쪽() {
		LocalDate date = LocalDate.of(2026, 3, 20);
		SolarPosition morning = calc.position(date.atTime(9, 0).atZone(SEOUL).toInstant(), LAT, LON);
		SolarPosition afternoon = calc.position(date.atTime(16, 0).atZone(SEOUL).toInstant(), LAT, LON);
		assertThat(morning.azimuthDeg()).isBetween(90.0, 180.0);
		assertThat(afternoon.azimuthDeg()).isBetween(180.0, 270.0);
	}

	@Test
	void 한밤중에는_해가_지평선_아래() {
		SolarPosition p = calc.position(LocalDate.of(2026, 6, 21).atTime(0, 0).atZone(SEOUL).toInstant(), LAT, LON);
		assertThat(p.aboveHorizon()).isFalse();
	}

	@Test
	void 부산_2026_11_08_일출은_약_06시_52분() {
		ZonedDateTime sunrise = calc.sunrise(LocalDate.of(2026, 11, 8), LAT, LON, SEOUL).orElseThrow();
		assertThat(sunrise.toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 8));
		long diffMinutes = Duration.between(LocalTime.of(6, 52), sunrise.toLocalTime()).abs().toMinutes();
		assertThat(diffMinutes).isLessThanOrEqualTo(2);
	}

	@Test
	void 일출_시각의_해는_지평선_근처() {
		LocalDate date = LocalDate.of(2026, 6, 21);
		ZonedDateTime sunrise = calc.sunrise(date, LAT, LON, SEOUL).orElseThrow();
		ZonedDateTime sunset = calc.sunset(date, LAT, LON, SEOUL).orElseThrow();
		assertThat(calc.position(sunrise.toInstant(), LAT, LON).altitudeDeg()).isCloseTo(-0.833, within(0.1));
		assertThat(calc.position(sunset.toInstant(), LAT, LON).altitudeDeg()).isCloseTo(-0.833, within(0.1));
		assertThat(sunset).isAfter(sunrise);
	}

	@Test
	void 극지방_백야에는_일출이_없다() {
		assertThat(calc.sunrise(LocalDate.of(2026, 6, 21), 80.0, 15.0, ZoneId.of("UTC"))).isEmpty();
	}
}
