package com.springboot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springboot.domain.Building;
import com.springboot.service.BuildingService;
import com.springboot.domain.HeightSource;
import com.springboot.exception.NotFoundException;

/** GeoPackage → building 테이블 가져오기와, 가져온 행을 JPA·서비스로 읽는 것까지 확인한다. */
@SpringBootTest
class BuildingImporterTest {

	@TempDir
	Path dir;

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BuildingService service;

	BuildingImporter.Result result;

	@BeforeEach
	void importSample() throws Exception {
		Path gpkg = dir.resolve("saha.gpkg");
		GpkgTestData.createGpkg(gpkg);
		try (Connection c = dataSource.getConnection()) {
			result = BuildingImporter.run(gpkg, c);
		}
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM building");
	}

	@Test
	void GeoPackage를_building_테이블로_가져온다() {
		assertThat(result.imported()).isEqualTo(5);
		assertThat(result.skipped()).isEqualTo(1);
		assertThat(result.bySource().get(HeightSource.MEASURED)).isEqualTo(1);
		assertThat(result.bySource().get(HeightSource.FLOOR_ESTIMATE)).isEqualTo(2);
		assertThat(result.bySource().get(HeightSource.DEFAULT)).isEqualTo(2);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM building WHERE dong = '하단동' AND jibun = '1160-6'");
		assertThat(row.get("legal_dong_name")).isEqualTo("부산광역시 사하구 하단동");
		assertThat(row.get("building_usage")).isEqualTo("공동주택");
		assertThat(((Number) row.get("area")).doubleValue()).isCloseTo(300.0, within(1e-6));
		assertThat(((Number) row.get("latitude")).doubleValue()).isCloseTo(35.1154, within(0.0001));
	}

	@Test
	void 다시_실행하면_비우고_새로_채운다() throws Exception {
		Path gpkg = dir.resolve("saha.gpkg");
		try (Connection c = dataSource.getConnection()) {
			BuildingImporter.run(gpkg, c);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM building", Integer.class)).isEqualTo(5);
	}

	@Test
	void 가져온_건물을_동_지번으로_찾는다() {
		Building b = service.findByAddress("하단동", " 1160-6 ").get(0);
		assertThat(b.getHeight()).isEqualTo(18.75);
		assertThat(b.getCenterX()).isCloseTo(378731.47, within(1e-6));
		assertThatThrownBy(() -> service.findByAddress("하단동", "1")).isInstanceOf(NotFoundException.class);
		assertThat(service.dongs()).containsExactly("괴정동", "하단동");
	}

	@Test
	void 반경_150m_안의_건물을_가까운_순으로() {
		BuildingService.Nearby nearby = service.nearby("하단동", "1160-6", 150);
		assertThat(nearby.neighbors()).extracting(n -> n.building().getJibun()).containsExactly("1160-7", "1161");
		assertThat(nearby.neighbors().get(0).distance()).isCloseTo(50.0, within(1e-6));
		assertThat(nearby.neighbors().get(0).azimuth()).isCloseTo(0.0, within(1e-6));
		assertThat(nearby.neighbors().get(1).azimuth()).isCloseTo(90.0, within(1e-6));
		assertThat(nearby.neighbors().get(1).building().getHeight()).isCloseTo(15.0, within(1e-9));
	}

	@Test
	void 법정동명의_마지막_낱말이_동() {
		assertThat(BuildingImporter.lastWord("부산광역시 사하구 하단동")).isEqualTo("하단동");
	}
}
