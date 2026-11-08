package com.plantspot.sun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class SunServiceTest {

	private final SunService service = new SunService(new SolarCalculator());

	@Test
	void 하루를_15분_간격으로_나누면_96개() {
		List<SunService.TimedPosition> list = service.positionsOfDay(LocalDate.of(2026, 12, 21),
				SunService.DEFAULT_LATITUDE, SunService.DEFAULT_LONGITUDE, 15);
		assertThat(list).hasSize(96);
		assertThat(list.get(0).time().toLocalTime().toString()).isEqualTo("00:00");
		assertThat(list.get(95).time().toLocalTime().toString()).isEqualTo("23:45");
	}

	@Test
	void 간격이_1440의_약수가_아니면_오류() {
		assertThatThrownBy(() -> service.positionsOfDay(LocalDate.of(2026, 1, 1), 35, 129, 7))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 일출_남중_일몰_순서() {
		SunService.SunDay day = service.day(LocalDate.of(2026, 3, 20), SunService.DEFAULT_LATITUDE,
				SunService.DEFAULT_LONGITUDE);
		assertThat(day.sunrise()).isBefore(day.solarNoon());
		assertThat(day.solarNoon()).isBefore(day.sunset());
	}
}
