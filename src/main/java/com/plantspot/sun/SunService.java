package com.plantspot.sun;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/** 한국 시각(Asia/Seoul) 기준으로 해 위치를 구한다. */
@Service
public class SunService {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 위치를 주지 않았을 때 쓰는 기본 위치: 부산 사하구 하단동 1160-6. */
	public static final double DEFAULT_LATITUDE = 35.1154;
	public static final double DEFAULT_LONGITUDE = 128.9605;

	private final SolarCalculator calculator;

	public SunService(SolarCalculator calculator) {
		this.calculator = calculator;
	}

	/** 한국 시각의 해 위치. */
	public SolarPosition position(LocalDateTime koreanDateTime, double latitude, double longitude) {
		return calculator.position(koreanDateTime.atZone(SEOUL).toInstant(), latitude, longitude);
	}

	/** 그 날의 일출·남중·일몰. */
	public SunDay day(LocalDate date, double latitude, double longitude) {
		return new SunDay(
				calculator.sunrise(date, latitude, longitude, SEOUL).map(ZonedDateTime::toLocalDateTime).orElse(null),
				calculator.solarNoon(date, latitude, longitude, SEOUL).toLocalDateTime(),
				calculator.sunset(date, latitude, longitude, SEOUL).map(ZonedDateTime::toLocalDateTime).orElse(null));
	}

	/** 하루(0시~24시)를 stepMinutes 간격으로 나눈 해 위치 목록. 칸별 햇빛 계산(3단계)에 쓴다. */
	public List<TimedPosition> positionsOfDay(LocalDate date, double latitude, double longitude, int stepMinutes) {
		if (stepMinutes <= 0 || 1440 % stepMinutes != 0) {
			throw new IllegalArgumentException("간격(분)은 1440의 약수여야 합니다: " + stepMinutes);
		}
		List<TimedPosition> result = new ArrayList<>(1440 / stepMinutes);
		for (int minute = 0; minute < 1440; minute += stepMinutes) {
			LocalDateTime time = date.atStartOfDay().plusMinutes(minute);
			result.add(new TimedPosition(time, position(time, latitude, longitude)));
		}
		return result;
	}

	public record SunDay(LocalDateTime sunrise, LocalDateTime solarNoon, LocalDateTime sunset) {
	}

	public record TimedPosition(LocalDateTime time, SolarPosition position) {
	}
}
