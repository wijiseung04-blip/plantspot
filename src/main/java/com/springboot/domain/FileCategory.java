package com.springboot.domain;

import java.util.Set;

/** 업로드 파일 종류와 허용 확장자. [필수: 파일 업로드] */
public enum FileCategory {
	SENSOR_LOG("센서 기록 파일", Set.of("csv", "txt", "log")),
	FLOOR_PLAN("평면도 이미지", Set.of("png", "jpg", "jpeg", "webp", "gif")),
	PLANT_PHOTO("식물 사진", Set.of("png", "jpg", "jpeg", "webp", "gif", "heic"));

	private final String label;
	private final Set<String> extensions;

	FileCategory(String label, Set<String> extensions) {
		this.label = label;
		this.extensions = extensions;
	}

	public String getLabel() {
		return label;
	}

	public Set<String> getExtensions() {
		return extensions;
	}

	public boolean isImage() {
		return this != SENSOR_LOG;
	}
}
