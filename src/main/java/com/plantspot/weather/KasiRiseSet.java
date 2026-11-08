package com.plantspot.weather;

import java.time.LocalTime;

/** 출몰시각 API 응답 한 건. 값이 없으면(백야 등) null. */
public record KasiRiseSet(LocalTime sunrise, LocalTime solarNoon, LocalTime sunset, LocalTime civilDawn,
		LocalTime civilDusk, LocalTime nauticalDawn, LocalTime nauticalDusk, LocalTime astroDawn,
		LocalTime astroDusk) {
}
