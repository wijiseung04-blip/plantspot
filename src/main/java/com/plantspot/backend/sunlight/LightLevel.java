package com.plantspot.backend.sunlight;

/**
 * 칸 밝기 단계. 기준은 농사로 광요구도와 맞춘다:
 * 낮은 광도 300~800 lux / 중간 800~1,500 / 높은 1,500~10,000.
 */
public enum LightLevel {
	DIM("어두움"),
	MEDIUM("보통"),
	BRIGHT("밝음");

	public static final int MEDIUM_LUX = 800;
	public static final int BRIGHT_LUX = 1500;
	/** 직사광이 이만큼(분) 들면 밝음 */
	public static final int BRIGHT_DIRECT_MIN = 120;
	public static final int MEDIUM_DIRECT_MIN = 30;

	private final String label;

	LightLevel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static LightLevel of(double lux, int directMinutes) {
		if (directMinutes >= BRIGHT_DIRECT_MIN || lux >= BRIGHT_LUX) {
			return BRIGHT;
		}
		if (directMinutes >= MEDIUM_DIRECT_MIN || lux >= MEDIUM_LUX) {
			return MEDIUM;
		}
		return DIM;
	}
}
