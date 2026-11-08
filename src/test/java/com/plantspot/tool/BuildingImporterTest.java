package com.plantspot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.plantspot.building.HeightSource;

class BuildingImporterTest {

	@TempDir
	Path dir;

	@Test
	void GeoPackage를_building_테이블로_가져온다() throws Exception {
		Path gpkg = dir.resolve("saha.gpkg");
		Path db = dir.resolve("plantspot.db");
		GpkgTestData.createGpkg(gpkg);

		BuildingImporter.Result r = BuildingImporter.run(gpkg, db);

		assertThat(r.imported()).isEqualTo(5);
		assertThat(r.skipped()).isEqualTo(1);
		assertThat(r.bySource().get(HeightSource.MEASURED)).isEqualTo(1);
		assertThat(r.bySource().get(HeightSource.FLOOR_ESTIMATE)).isEqualTo(2);
		assertThat(r.bySource().get(HeightSource.DEFAULT)).isEqualTo(2);

		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery(
						"SELECT * FROM building WHERE dong = '하단동' AND jibun = '1160-6'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString("legal_dong_name")).isEqualTo("부산광역시 사하구 하단동");
			assertThat(rs.getDouble("area")).isCloseTo(300.0, within(1e-6));
			assertThat(rs.getDouble("height")).isEqualTo(18.75);
			assertThat(rs.getString("height_source")).isEqualTo("MEASURED");
			assertThat(rs.getDouble("latitude")).isCloseTo(35.1154, within(0.0001));
			assertThat(rs.getDouble("longitude")).isCloseTo(128.9605, within(0.0001));
		}
	}

	@Test
	void 다시_실행하면_비우고_새로_채운다() throws Exception {
		Path gpkg = dir.resolve("saha.gpkg");
		Path db = dir.resolve("plantspot.db");
		GpkgTestData.createGpkg(gpkg);
		BuildingImporter.run(gpkg, db);
		BuildingImporter.run(gpkg, db);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("SELECT count(*) FROM building")) {
			rs.next();
			assertThat(rs.getInt(1)).isEqualTo(5);
		}
	}

	@Test
	void 법정동명의_마지막_낱말이_동() {
		assertThat(BuildingImporter.lastWord("부산광역시 사하구 하단동")).isEqualTo("하단동");
	}
}
