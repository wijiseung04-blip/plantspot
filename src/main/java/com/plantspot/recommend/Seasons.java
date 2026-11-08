package com.plantspot.recommend;

import java.time.LocalDate;

import com.plantspot.sunlight.SeasonDay;

/** 날짜 → 계절 대표일 (3~5월 춘분, 6~8월 하지, 9~11월 추분, 12~2월 동지). */
public final class Seasons {

	private Seasons() {
	}

	public static SeasonDay of(LocalDate date) {
		int m = date.getMonthValue();
		if (m >= 3 && m <= 5) {
			return SeasonDay.SPRING_EQUINOX;
		}
		if (m >= 6 && m <= 8) {
			return SeasonDay.SUMMER_SOLSTICE;
		}
		if (m >= 9 && m <= 11) {
			return SeasonDay.AUTUMN_EQUINOX;
		}
		return SeasonDay.WINTER_SOLSTICE;
	}

	public static SeasonDay next(SeasonDay s) {
		return SeasonDay.values()[(s.ordinal() + 1) % 4];
	}
}
