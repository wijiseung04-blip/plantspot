package com.plantspot.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.plantspot.building.HeightRule;
import com.plantspot.building.HeightSource;
import com.plantspot.geo.TmConverter;
import com.plantspot.geo.TmConverter.LatLon;

/**
 * [① DB 데이터 생성 프로그램] 국토부 GIS 건물통합정보(QGIS에서 사하구만 저장한 GeoPackage)를 읽어
 * building 테이블로 변환한다. 스프링 없이 JDBC만 쓰는 단독 프로그램.
 *
 * <pre>
 * ./gradlew importBuildings -Pgpkg=C:/data/saha.gpkg            (DB 기본값: data/plantspot.db)
 * ./gradlew importBuildings -Pgpkg=saha.gpkg -Pdb=other.db
 * </pre>
 *
 * 하는 일: 도형 → 넓이·중심점(EPSG:5186) → 위경도, 높이·층수·용도 → HeightRule 최종 높이.
 * 다시 실행하면 building 테이블을 비우고 새로 채운다.
 */
public final class BuildingImporter {

	static final String CREATE_TABLE = """
			CREATE TABLE IF NOT EXISTS building (
			  id integer PRIMARY KEY AUTOINCREMENT,
			  pnu varchar(255),
			  legal_dong_name varchar(255),
			  dong varchar(255),
			  jibun varchar(255),
			  usage varchar(255),
			  approval_date varchar(255),
			  building_name varchar(255),
			  dong_label varchar(255),
			  ground_floors integer,
			  basement_floors integer,
			  raw_height float,
			  height float NOT NULL,
			  height_source varchar(20),
			  area float NOT NULL,
			  center_x float NOT NULL,
			  center_y float NOT NULL,
			  latitude float NOT NULL,
			  longitude float NOT NULL,
			  footprint_wkt TEXT
			)""";

	private static final String INSERT = """
			INSERT INTO building (pnu, legal_dong_name, dong, jibun, usage, approval_date, building_name, dong_label,
			  ground_floors, basement_floors, raw_height, height, height_source, area, center_x, center_y,
			  latitude, longitude, footprint_wkt)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

	private static final int BATCH_SIZE = 1000;

	private BuildingImporter() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args[0].isBlank()) {
			System.err.println("사용법: ./gradlew importBuildings -Pgpkg=<GeoPackage 경로> [-Pdb=<DB 경로>]");
			System.exit(1);
		}
		Path gpkg = Path.of(args[0]);
		Path db = Path.of(args.length > 1 && !args[1].isBlank() ? args[1] : "data/plantspot.db");
		Result result = run(gpkg, db);
		System.out.println(result.summary());
	}

	/** 결과 통계. */
	public record Result(int imported, int skipped, Map<HeightSource, Integer> bySource) {

		public String summary() {
			return String.format(Locale.ROOT, "가져온 건물 %,d동 (건너뜀 %,d) — MEASURED %,d / FLOOR_ESTIMATE %,d / DEFAULT %,d",
					imported, skipped, bySource.getOrDefault(HeightSource.MEASURED, 0),
					bySource.getOrDefault(HeightSource.FLOOR_ESTIMATE, 0),
					bySource.getOrDefault(HeightSource.DEFAULT, 0));
		}
	}

	public static Result run(Path gpkg, Path db) throws SQLException, java.io.IOException {
		if (!Files.isRegularFile(gpkg)) {
			throw new IllegalArgumentException("GeoPackage 파일이 없습니다: " + gpkg.toAbsolutePath());
		}
		if (db.toAbsolutePath().getParent() != null) {
			Files.createDirectories(db.toAbsolutePath().getParent());
		}

		try (Connection src = DriverManager.getConnection("jdbc:sqlite:" + gpkg.toAbsolutePath());
				Connection dst = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
			String[] layer = findFeatureLayer(src);
			String table = layer[0];
			String geomColumn = layer[1];
			System.out.println("레이어: " + table + " (도형 칸: " + geomColumn + ")");

			try (Statement st = dst.createStatement()) {
				st.execute(CREATE_TABLE);
				st.execute("CREATE INDEX IF NOT EXISTS idx_building_dong_jibun ON building (dong, jibun)");
				st.execute("CREATE INDEX IF NOT EXISTS idx_building_center ON building (center_x, center_y)");
			}
			dst.setAutoCommit(false);
			try (Statement st = dst.createStatement()) {
				st.execute("DELETE FROM building");
			}

			int imported = 0;
			int skipped = 0;
			Map<HeightSource, Integer> bySource = new EnumMap<>(HeightSource.class);

			try (Statement st = src.createStatement();
					ResultSet rs = st.executeQuery("SELECT * FROM \"" + table.replace("\"", "\"\"") + "\"");
					PreparedStatement ins = dst.prepareStatement(INSERT)) {
				Map<String, Integer> columns = columnIndex(rs.getMetaData());
				int geomIndex = columns.get(geomColumn.toUpperCase(Locale.ROOT));

				while (rs.next()) {
					byte[] blob = rs.getBytes(geomIndex);
					List<GpkgGeometryReader.Polygon> polygons;
					try {
						polygons = blob == null ? List.of() : GpkgGeometryReader.read(blob);
					} catch (RuntimeException e) {
						skipped++;
						continue;
					}
					if (polygons.isEmpty()) {
						skipped++;
						continue;
					}
					GpkgGeometryReader.Shape shape = GpkgGeometryReader.shape(polygons);
					LatLon ll = TmConverter.toLatLon(shape.centerX(), shape.centerY());

					String legalDong = text(rs, columns, "A4");
					String usage = text(rs, columns, "A9");
					Double rawHeight = number(rs, columns, "A16");
					Integer groundFloors = integer(rs, columns, "A26");
					HeightRule.Result height = HeightRule.decide(rawHeight, groundFloors, usage);

					int i = 1;
					ins.setString(i++, text(rs, columns, "A2"));
					ins.setString(i++, legalDong);
					ins.setString(i++, lastWord(legalDong));
					ins.setString(i++, text(rs, columns, "A5"));
					ins.setString(i++, usage);
					ins.setString(i++, text(rs, columns, "A13"));
					ins.setString(i++, text(rs, columns, "A24"));
					ins.setString(i++, text(rs, columns, "A25"));
					ins.setObject(i++, groundFloors);
					ins.setObject(i++, integer(rs, columns, "A27"));
					ins.setObject(i++, rawHeight);
					ins.setDouble(i++, height.height());
					ins.setString(i++, height.source().name());
					ins.setDouble(i++, shape.area());
					ins.setDouble(i++, shape.centerX());
					ins.setDouble(i++, shape.centerY());
					ins.setDouble(i++, ll.latitude());
					ins.setDouble(i++, ll.longitude());
					ins.setString(i++, GpkgGeometryReader.toWkt(polygons));
					ins.addBatch();

					bySource.merge(height.source(), 1, Integer::sum);
					imported++;
					if (imported % BATCH_SIZE == 0) {
						ins.executeBatch();
						System.out.printf(Locale.ROOT, "  %,d동...%n", imported);
					}
				}
				ins.executeBatch();
			}
			dst.commit();
			return new Result(imported, skipped, bySource);
		}
	}

	/** gpkg_geometry_columns에서 첫 번째 도형 레이어의 {테이블, 도형 칸}을 찾는다. */
	private static String[] findFeatureLayer(Connection src) throws SQLException {
		try (Statement st = src.createStatement();
				ResultSet rs = st.executeQuery("""
						SELECT g.table_name, g.column_name FROM gpkg_geometry_columns g
						JOIN gpkg_contents c ON c.table_name = g.table_name
						WHERE c.data_type = 'features' ORDER BY g.table_name""")) {
			if (!rs.next()) {
				throw new IllegalArgumentException("GeoPackage에 도형 레이어가 없습니다.");
			}
			return new String[] { rs.getString(1), rs.getString(2) };
		}
	}

	private static Map<String, Integer> columnIndex(ResultSetMetaData meta) throws SQLException {
		Map<String, Integer> map = new HashMap<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			map.put(meta.getColumnName(i).toUpperCase(Locale.ROOT), i);
		}
		return map;
	}

	private static String text(ResultSet rs, Map<String, Integer> columns, String name) throws SQLException {
		Integer idx = columns.get(name);
		if (idx == null) {
			return null;
		}
		String v = rs.getString(idx);
		if (v == null) {
			return null;
		}
		v = v.trim();
		return v.isEmpty() ? null : v;
	}

	/** 숫자 칸. 비어 있거나 숫자가 아니면 null. */
	private static Double number(ResultSet rs, Map<String, Integer> columns, String name) throws SQLException {
		String v = text(rs, columns, name);
		if (v == null) {
			return null;
		}
		try {
			return Double.valueOf(v);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer integer(ResultSet rs, Map<String, Integer> columns, String name) throws SQLException {
		Double v = number(rs, columns, name);
		return v == null ? null : (int) Math.round(v);
	}

	static String lastWord(String legalDong) {
		if (legalDong == null) {
			return null;
		}
		String[] words = legalDong.trim().split("\\s+");
		return words[words.length - 1];
	}
}
