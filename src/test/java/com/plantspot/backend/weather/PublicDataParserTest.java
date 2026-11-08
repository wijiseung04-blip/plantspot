package com.plantspot.backend.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class PublicDataParserTest {

	static byte[] resource(String name) {
		try (InputStream in = PublicDataParserTest.class.getResourceAsStream("/publicdata/" + name)) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void 출몰시각_읽기() {
		KasiRiseSet r = PublicDataParser.parseRiseSet(resource("kasi-riseset.xml"));
		assertThat(r.sunrise()).isEqualTo(LocalTime.of(6, 52));
		assertThat(r.solarNoon()).isEqualTo(LocalTime.of(12, 7));
		assertThat(r.sunset()).isEqualTo(LocalTime.of(17, 23));
		assertThat(r.civilDawn()).isEqualTo(LocalTime.of(6, 25));
		assertThat(r.civilDusk()).isEqualTo(LocalTime.of(17, 50));
	}

	@Test
	void 시각_문자열() {
		assertThat(PublicDataParser.time("0652  ")).isEqualTo(LocalTime.of(6, 52));
		assertThat(PublicDataParser.time("065230")).isEqualTo(LocalTime.of(6, 52));
		assertThat(PublicDataParser.time("----")).isNull();
		assertThat(PublicDataParser.time(null)).isNull();
	}

	@Test
	void 출몰시각_오류_코드() {
		byte[] err = "<response><header><resultCode>30</resultCode><resultMsg>SERVICE KEY IS NOT REGISTERED</resultMsg></header></response>"
				.getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> PublicDataParser.parseRiseSet(err)).hasMessageContaining("30");
	}

	@Test
	void ASOS_읽기() {
		PublicDataParser.AsosPage p = PublicDataParser.parseAsos(resource("asos-day.json"));
		assertThat(p.hours()).hasSize(3);
		WeatherHour first = p.hours().get(0);
		assertThat(first.getObservedAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 0));
		assertThat(first.getStationId()).isEqualTo(159);
		assertThat(first.getSunshineHours()).isEqualTo(1.0);
		assertThat(first.getInsolation()).isEqualTo(1.12);
		assertThat(first.isClear()).isTrue();
		assertThat(p.hours().get(1).isClear()).isFalse(); // 일조 0.2시간 → 흐림
		WeatherHour suspicious = p.hours().get(2);
		assertThat(suspicious.getTemperature()).isNull();
		assertThat(suspicious.isClear()).isFalse(); // 품질 의심 플래그
	}

	@Test
	void ASOS_자료_없음은_빈_결과() {
		byte[] noData = "{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NO_DATA\"}}}".getBytes();
		assertThat(PublicDataParser.parseAsos(noData).hours()).isEmpty();
	}
}
