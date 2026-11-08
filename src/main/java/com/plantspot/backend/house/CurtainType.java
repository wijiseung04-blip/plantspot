package com.plantspot.backend.house;

/** 커튼 종류와 빛 투과율(대략값, 센서 보정 전 기본값). */
public enum CurtainType {
	NONE("없음", 1.0),
	SHEER("얇은 커튼(속커튼)", 0.6),
	BLIND("블라인드", 0.5),
	THICK("두꺼운 커튼", 0.2),
	BLACKOUT("암막 커튼", 0.02);

	private final String label;
	private final double transmittance;

	CurtainType(String label, double transmittance) {
		this.label = label;
		this.transmittance = transmittance;
	}

	public String getLabel() {
		return label;
	}

	public double getTransmittance() {
		return transmittance;
	}
}
