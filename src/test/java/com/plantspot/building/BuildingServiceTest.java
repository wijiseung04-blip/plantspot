package com.plantspot.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.plantspot.common.NotFoundException;
import com.plantspot.tool.BuildingImporter;
import com.plantspot.tool.GpkgTestDataAccess;

/** BuildingImporter가 만든 테이블을 JPA 엔티티로 그대로 읽을 수 있는지까지 확인한다. */
@SpringBootTest
class BuildingServiceTest {

	private static Path dir;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		dir = Files.createTempDirectory("plantspot-test");
		Path gpkg = dir.resolve("saha.gpkg");
		Path db = dir.resolve("plantspot.db");
		GpkgTestDataAccess.createGpkg(gpkg);
		BuildingImporter.run(gpkg, db);
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.toAbsolutePath());
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
	}

	@AfterAll
	static void cleanUp() throws Exception {
		try (var files = Files.walk(dir)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Autowired
	BuildingService service;

	@Test
	void 동_지번으로_찾기() {
		Building b = service.findByAddress("하단동", " 1160-6 ").get(0);
		assertThat(b.getHeight()).isEqualTo(18.75);
		assertThat(b.getHeightSource()).isEqualTo(HeightSource.MEASURED);
		assertThat(b.getCenterX()).isCloseTo(378731.47, within(1e-6));
	}

	@Test
	void 없는_지번은_NotFound() {
		assertThatThrownBy(() -> service.findByAddress("하단동", "1"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void 반경_150m_안의_건물을_가까운_순으로() {
		BuildingService.Nearby nearby = service.nearby("하단동", "1160-6", 150);
		assertThat(nearby.neighbors()).extracting(n -> n.building().getJibun())
				.containsExactly("1160-7", "1161");
		BuildingService.Neighbor north = nearby.neighbors().get(0);
		assertThat(north.distance()).isCloseTo(50.0, within(1e-6));
		assertThat(north.azimuth()).isCloseTo(0.0, within(1e-6));
		BuildingService.Neighbor east = nearby.neighbors().get(1);
		assertThat(east.distance()).isCloseTo(100.0, within(1e-6));
		assertThat(east.azimuth()).isCloseTo(90.0, within(1e-6));
		assertThat(east.building().getHeight()).isCloseTo(15.0, within(1e-9));
	}

	@Test
	void 동_목록() {
		assertThat(service.dongs()).containsExactly("괴정동", "하단동");
	}
}
