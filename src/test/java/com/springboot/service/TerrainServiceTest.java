package com.springboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.Role;
import com.springboot.security.LoginMember;
import com.springboot.support.TestBuildings;

/** DEM 파일(ASCII Grid) → 격자 저장 → 가림 각도에 경사·산 반영 */
@SpringBootTest(properties = "plantspot.terrain.buffer=1500")
@AutoConfigureMockMvc
@Transactional
class TerrainServiceTest {

	static final double HX = 378731.47;
	static final double HY = 281659.43;

	@Autowired
	TerrainService terrain;

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	@TempDir
	Path dir;

	/**
	 * 집 둘레 ±3km 를 덮는 30m 격자 ASCII Grid. 남쪽으로 오르막인 비탈 (1m 남쪽 = 0.2m 높음, 북쪽은 내리막).
	 * 북서쪽 모서리 한 칸은 NODATA.
	 */
	private Path slopeFile() throws IOException {
		int n = 201;
		double cell = 30;
		double xll = HX - 3000 - cell / 2;
		double yll = HY - 3000 - cell / 2;
		StringBuilder sb = new StringBuilder();
		sb.append("ncols ").append(n).append("\nnrows ").append(n).append('\n');
		sb.append(String.format(Locale.ROOT, "xllcorner %.3f%nyllcorner %.3f%ncellsize %.0f%nNODATA_value -9999%n",
				xll, yll, cell));
		for (int row = 0; row < n; row++) {
			double y = yll + (n - 1 - row + 0.5) * cell;
			for (int col = 0; col < n; col++) {
				double z = row == 0 && col == 0 ? -9999 : 100 + (HY - y) * 0.2;
				sb.append(String.format(Locale.ROOT, "%.2f", z)).append(col + 1 < n ? ' ' : '\n');
			}
		}
		Path f = dir.resolve("dem.asc");
		Files.writeString(f, sb.toString());
		return f;
	}

	@Test
	void 파일에서_건물_범위만_잘라_저장하고_땅_높이를_보간한다() throws Exception {
		TestBuildings.insertHadan(jdbc);

		TerrainService.ImportResult r = terrain.importFile(slopeFile(), m -> {
		});

		// 여유 1.5km → 약 ±1.5km = 101×101 점 (6km 파일에서 잘라냄)
		assertThat(r.nx()).isBetween(100, 103);
		assertThat(r.points()).isGreaterThan(10_000);
		assertThat(terrain.groundAt(HX, HY)).isCloseTo(100, within(1.0));
		assertThat(terrain.groundAt(HX, HY - 500)).isCloseTo(200, within(1.0));
		assertThat(terrain.status().available()).isTrue();
		assertThat(terrain.status().source()).isEqualTo("dem.asc");

		// 다시 가져오면 이전 자료를 바꾼다
		TerrainService.ImportResult again = terrain.importFile(slopeFile(), m -> {
		});
		assertThat(jdbc.queryForObject("select count(*) from terrain_point", Integer.class)).isEqualTo(again.points());
		assertThat(jdbc.queryForObject("select count(*) from terrain_grid", Integer.class)).isEqualTo(1);
	}

	@Test
	void 좌표계가_다르면_알려준다() throws Exception {
		TestBuildings.insertHadan(jdbc);
		Path f = dir.resolve("wgs84.asc");
		Files.writeString(f, "ncols 2\nnrows 2\nxllcorner 128.9\nyllcorner 35.0\ncellsize 0.0003\n1 2\n3 4\n");
		assertThatThrownBy(() -> terrain.importFile(f, m -> {
		})).hasMessageContaining("EPSG:5186");
	}

	@Test
	void 오르막_위_건물과_산이_가림_각도에_들어간다() throws Exception {
		TestBuildings.insertHadan(jdbc);
		// 정남 50m, 높이 10m 건물 → 평지라면 atan(8.8/50) = 10.0°, 땅이 10m 높아 atan(18.8/50) = 20.6°
		TestBuildings.insert(jdbc, "하단동", "2000", HX, HY - 50, 10, "MEASURED");
		terrain.importFile(slopeFile(), m -> {
		});

		LoginMember owner = new LoginMember(memberService.create("tterrain", "password123", "주인", Role.USER));
		String house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":1}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");

		String json = mvc.perform(get(house + "/obstructions").with(user(owner)))
				.andExpect(jsonPath("$[36].azimuth").value(180))
				.andReturn().getResponse().getContentAsString();
		double onlyBuilding = com.jayway.jsonpath.JsonPath.<Double>read(json, "$[36].onlyBuildingAngle");
		double land = com.jayway.jsonpath.JsonPath.<Double>read(json, "$[36].terrainAngle");
		double north = com.jayway.jsonpath.JsonPath.<Double>read(json, "$[0].angle");
		assertThat(onlyBuilding).isCloseTo(20.6, within(1.5));
		// 오르막 산: atan((0.2d − 1.2) / d) ≈ 11°
		assertThat(land).isCloseTo(11.0, within(1.0));
		assertThat(north).isZero();
	}
}
