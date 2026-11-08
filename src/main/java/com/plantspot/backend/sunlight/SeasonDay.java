package com.plantspot.backend.sunlight;

import java.time.LocalDate;

/** 계절 대표일 4개. (날짜는 해마다 하루 정도 달라질 수 있으나 햇빛 계산에는 차이가 거의 없다) */
public enum SeasonDay {
	SPRING_EQUINOX("춘분", 3, 20),
	SUMMER_SOLSTICE("하지", 6, 21),
	AUTUMN_EQUINOX("추분", 9, 23),
	WINTER_SOLSTICE("동지", 12, 22);

	private final String label;
	private final int month;
	private final int day;

	SeasonDay(String label, int month, int day) {
		this.label = label;
		this.month = month;
		this.day = day;
	}

	public String getLabel() {
		return label;
	}

	public LocalDate date(int year) {
		return LocalDate.of(year, month, day);
	}
}
