package com.plantspot.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트용 건물 행 넣기 (Building 엔티티는 가져오기 프로그램만 만들기 때문에 JDBC로 넣는다). */
public final class TestBuildings {

	private TestBuildings() {
	}

	public static void insert(JdbcTemplate jdbc, String dong, String jibun, double x, double y, double height,
			String source) {
		jdbc.update("""
				INSERT INTO building (legal_dong_name, dong, jibun, usage, height, height_source, area,
				  center_x, center_y, latitude, longitude, ground_floors)
				VALUES (?, ?, ?, '공동주택', ?, ?, 300, ?, ?, ?, ?, 6)""",
				"부산광역시 사하구 " + dong, dong, jibun, height, source, x, y,
				com.plantspot.geo.TmConverter.toLatLon(x, y).latitude(),
				com.plantspot.geo.TmConverter.toLatLon(x, y).longitude());
	}

	/** 하단동 1160-6 (기준 건물) */
	public static void insertHadan(JdbcTemplate jdbc) {
		insert(jdbc, "하단동", "1160-6", 378731.47, 281659.43, 18.75, "MEASURED");
	}
}
