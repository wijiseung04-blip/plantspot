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

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.plantspot.building.HeightRule;
import com.plantspot.building.HeightSource;
import com.plantspot.geo.TmConverter;
import com.plantspot.geo.TmConverter.LatLon;

/**
 * [① DB 데이터 생성 프로그램] 국토부 GIS 건물통합정보(QGIS에서 사하구만 저장한 GeoPackage)를 읽어
 * building 테이블(SQLite data/plantspot.db)로 변환한다.
 *
 * <pre>
 * ./gradlew :backend:importBuildings -Pgpkg=C:/data/saha.gpkg
 * </pre>
 *
 * 하는 일: 도형 → 넓이·중심점(EPSG:5186) → 위경도, 높이·층수·용도 → HeightRule 최종 높이.
 * 다시 실행하면 building 테이블을 비우고 새로 채운 뒤, 등록된 집(house)을 동·지번으로 다시 연결한다.
 * 테이블은 JPA(Building 엔티티)가 만든다.
 */
@Component
@ConditionalOnProperty(name = "plantspot.import", havingValue = "buildings")
public class BuildingImporter implements CommandLineRunner {

	private static final String INSERT = """
			INSERT INTO building (pnu, legal_dong_name, dong, jibun, building_usage, approval_date, building_name, dong_label,
			  ground_floors, basement_floors, raw_height, height, height_source, area, center_x, center_y,
			  latitude, longitude, footprint_wkt)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

	private static final int BATCH_SIZE = 1000;

	private final DataSource dataSource;
	private final ApplicationContext context;
	private final String gpkg;

	public BuildingImporter(DataSource dataSource, ApplicationContext context, @Value("${gpkg:}") String gpkg) {
		this.dataSource = dataSource;
		this.context = context;
		this.gpkg = gpkg;
	}

	@Override
	public void run(String... args) throws Exception {
		if (gpkg.isBlank()) {
			System.err.println("사용법: ./gradlew :backend:importBuildings -Pgpkg=<GeoPackage 경로>");
			System.exit(SpringApplication.exit(context, () -> 1));
		}
		Result result;
		try (Connection dst = dataSource.getConnection()) {
			result = run(Path.of(gpkg), dst);
		}
		System.out.println(result.summary());
		System.exit(SpringApplication.exit(context, () -> 0));
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

	public static Result run(Path gpkg, Connection dst) throws SQLException {
		if (!Files.isRegularFile(gpkg)) {
			throw new IllegalArgumentException("GeoPackage 파일이 없습니다: " + gpkg.toAbsolutePath());
		}
		try (Connection src = DriverManager.getConnection("jdbc:sqlite:" + gpkg.toAbsolutePath())) {
			String[] layer = findFeatureLayer(src);
			String table = layer[0];
			String geomColumn = layer[1];
			System.out.println("레이어: " + table + " (도형 칸: " + geomColumn + ")");

			boolean autoCommit = dst.getAutoCommit();
			dst.setAutoCommit(false);
			try (Statement st = dst.createStatement()) {
				// 집이 건물을 가리키고 있으면 지울 수 없으므로 연결을 잠시 끊는다(가져온 뒤 동·지번으로 다시 연결)
				st.executeUpdate("UPDATE house SET building_id = NULL");
				st.executeUpdate("DELETE FROM building");
			} catch (SQLException e) {
				// house 테이블이 아직 없을 수 있다
				dst.rollback();
				try (Statement st = dst.createStatement()) {
					st.executeUpdate("DELETE FROM building");
				}
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
			relinkHouses(dst);
			dst.commit();
			dst.setAutoCommit(autoCommit);
			return new Result(imported, skipped, bySource);
		}
	}

	/** 등록된 집을 동·지번이 같은 건물 중 가장 큰 건물에 다시 연결한다. */
	private static void relinkHouses(Connection dst) {
		try (Statement st = dst.createStatement()) {
			st.executeUpdate("""
					UPDATE house SET building_id = (
					  SELECT b.id FROM building b WHERE b.dong = house.dong AND b.jibun = house.jibun
					  ORDER BY b.area DESC LIMIT 1)""");
		} catch (SQLException e) {
			// house 테이블이 없으면 할 일 없음
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
