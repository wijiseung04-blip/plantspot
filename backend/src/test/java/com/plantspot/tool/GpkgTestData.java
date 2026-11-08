package com.plantspot.tool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.file.Path;

/** 테스트용 GeoPackage 도형·파일 만들기. */
final class GpkgTestData {

	private GpkgTestData() {
	}

	/** 사각형 Polygon 하나 (GP 헤더: 리틀 엔디언, envelope 없음). */
	static byte[] rectangle(double x, double y, double w, double h) {
		double[][] ring = { { x, y }, { x + w, y }, { x + w, y + h }, { x, y + h }, { x, y } };
		ByteBuffer b = ByteBuffer.allocate(8 + 1 + 4 + 4 + 4 + ring.length * 16).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 0b0000_0001).putInt(5186);
		b.put((byte) 1).putInt(3).putInt(1).putInt(ring.length);
		for (double[] p : ring) {
			b.putDouble(p[0]).putDouble(p[1]);
		}
		return b.array();
	}

	/** 바깥 사각형 + 구멍 사각형을 MultiPolygon(빅 엔디언 WKB, XY envelope 포함)으로. */
	static byte[] multiPolygonWithHole() {
		double[][] outer = { { 0, 0 }, { 10, 0 }, { 10, 10 }, { 0, 10 }, { 0, 0 } };
		double[][] hole = { { 2, 2 }, { 2, 4 }, { 4, 4 }, { 4, 2 }, { 2, 2 } };
		ByteBuffer b = ByteBuffer.allocate(8 + 32 + 9 + 9 + 4 + 2 * (4 + 5 * 16)).order(ByteOrder.BIG_ENDIAN);
		b.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 0b0000_0010).putInt(5186);
		b.putDouble(0).putDouble(10).putDouble(0).putDouble(10);
		b.put((byte) 0).putInt(6).putInt(1);
		b.put((byte) 0).putInt(3).putInt(2);
		for (double[][] ring : new double[][][] { outer, hole }) {
			b.putInt(ring.length);
			for (double[] p : ring) {
				b.putDouble(p[0]).putDouble(p[1]);
			}
		}
		return b.array();
	}

	/** QGIS가 저장하는 것과 같은 최소 GeoPackage 구조. */
	static void createGpkg(Path file) throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
				Statement st = c.createStatement()) {
			st.execute("CREATE TABLE gpkg_contents (table_name TEXT PRIMARY KEY, data_type TEXT NOT NULL)");
			st.execute("CREATE TABLE gpkg_geometry_columns (table_name TEXT, column_name TEXT)");
			st.execute("INSERT INTO gpkg_contents VALUES ('saha', 'features')");
			st.execute("INSERT INTO gpkg_geometry_columns VALUES ('saha', 'geom')");
			st.execute("""
					CREATE TABLE saha (fid INTEGER PRIMARY KEY, geom BLOB, A2 TEXT, A4 TEXT, A5 TEXT, A9 TEXT,
					  A13 TEXT, A16 REAL, A24 TEXT, A25 TEXT, A26 INTEGER, A27 INTEGER)""");
			try (PreparedStatement ps = c.prepareStatement(
					"INSERT INTO saha (geom, A4, A5, A9, A16, A26) VALUES (?, ?, ?, ?, ?, ?)")) {
				// 하단동 1160-6: 중심 (378731.47, 281659.43), 20m × 15m = 300㎡, 높이 18.75m
				insert(ps, rectangle(378721.47, 281651.93, 20, 15), "부산광역시 사하구 하단동", "1160-6", "공동주택", 18.75, 6);
				// 북쪽 50m, 층수만 있음
				insert(ps, rectangle(378726.47, 281704.43, 10, 10), "부산광역시 사하구 하단동", "1160-7", "단독주택", null, 2);
				// 동쪽 100m, 높이 오류(15,400m) + 층수
				insert(ps, rectangle(378826.47, 281654.43, 10, 10), "부산광역시 사하구 하단동", "1161", "공동주택", 15400.0, 5);
				// 남쪽 400m, 정보 없음 → 반경 150m 밖
				insert(ps, rectangle(378726.47, 281254.43, 10, 10), "부산광역시 사하구 하단동", "1200", null, null, null);
				// 다른 동
				insert(ps, rectangle(380000, 280000, 10, 10), "부산광역시 사하구 괴정동", "1", null, null, null);
				// 도형 없음 → 건너뜀
				insert(ps, null, "부산광역시 사하구 하단동", "9999", null, null, null);
			}
		}
	}

	private static void insert(PreparedStatement ps, byte[] geom, String a4, String a5, String a9, Double a16,
			Integer a26) throws Exception {
		ps.setBytes(1, geom);
		ps.setString(2, a4);
		ps.setString(3, a5);
		ps.setString(4, a9);
		ps.setObject(5, a16);
		ps.setObject(6, a26);
		ps.executeUpdate();
	}
}
