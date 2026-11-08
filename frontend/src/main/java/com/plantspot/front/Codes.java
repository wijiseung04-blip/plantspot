package com.plantspot.front;

import java.util.List;

/** 화면 선택 목록 (backend의 enum 값과 같아야 한다). */
public final class Codes {

	private Codes() {
	}

	public record Option(String value, String label) {
	}

	public record FileCategory(String value, String label, String extensions) {
	}

	/** backend house.CurtainType */
	public static final List<Option> CURTAINS = List.of(
			new Option("NONE", "없음"), new Option("SHEER", "얇은 커튼(속커튼)"), new Option("BLIND", "블라인드"),
			new Option("THICK", "두꺼운 커튼"), new Option("BLACKOUT", "암막 커튼"));

	/** backend sunlight.SeasonDay */
	public static final List<Option> SEASONS = List.of(
			new Option("SPRING_EQUINOX", "춘분"), new Option("SUMMER_SOLSTICE", "하지"),
			new Option("AUTUMN_EQUINOX", "추분"), new Option("WINTER_SOLSTICE", "동지"));

	/** backend file.FileCategory */
	public static final List<FileCategory> FILE_CATEGORIES = List.of(
			new FileCategory("SENSOR_LOG", "센서 기록 파일", "csv, log, txt"),
			new FileCategory("FLOOR_PLAN", "평면도 이미지", "gif, jpeg, jpg, png, webp"),
			new FileCategory("PLANT_PHOTO", "식물 사진", "gif, heic, jpeg, jpg, png, webp"));

	/** 농사로 광요구도 코드 */
	public static final List<Option> LIGHTS = List.of(
			new Option("055001", "낮은 광도(300~800 lux)"), new Option("055002", "중간 광도(800~1,500 lux)"),
			new Option("055003", "높은 광도(1,500~10,000 lux)"));
}
