package com.springboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.calculator.TmConverter;
import com.springboot.calculator.TmConverter.LatLon;
import com.springboot.client.ElevationApi;
import com.springboot.domain.Role;
import com.springboot.security.LoginMember;
import com.springboot.support.TestBuildings;

/** 고도 API(가짜) → 격자 저장 → 가림 각도에 경사·산 반영 */
@SpringBootTest(properties = { "plantspot.terrain.buffer=1500", "plantspot.terrain.delay-ms=0" })
@AutoConfigureMockMvc
@Transactional
class TerrainServiceTest {

	static final double HX = 378731.47;
	static final double HY = 281659.43;

	@MockitoBean
	ElevationApi api;

	@Autowired
	TerrainService terrain;

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	/** 남쪽으로 오르막인 비탈 (1m 남쪽 = 0.2m 높음, 북쪽은 내리막) */
	private void slopeToSouth() {
		given(api.maxBatch()).willReturn(100);
		given(api.sourceName()).willReturn("가짜");
		given(api.elevations(anyList())).willAnswer(inv -> {
			List<LatLon> pts = inv.getArgument(0);
			double[] z = new double[pts.size()];
			for (int i = 0; i < z.length; i++) {
				TmConverter.Tm tm = TmConverter.fromLatLon(pts.get(i).latitude(), pts.get(i).longitude());
				z[i] = 100 + (HY - tm.y()) * 0.2;
			}
			return z;
		});
	}

	@Test
	void 격자를_받아_저장하고_땅_높이를_보간한다() {
		TestBuildings.insertHadan(jdbc);
		slopeToSouth();

		TerrainService.ImportResult r = terrain.importAll(p -> {
		});

		assertThat(r.fetched()).isEqualTo(r.total()).isGreaterThan(1000);
		assertThat(terrain.groundAt(HX, HY)).isCloseTo(100, within(10.0));
		assertThat(terrain.groundAt(HX, HY - 500)).isCloseTo(200, within(10.0));
		assertThat(terrain.status().available()).isTrue();

		// 다시 실행하면 받은 점은 건너뛴다
		TerrainService.ImportResult again = terrain.importAll(p -> {
		});
		assertThat(again.fetched()).isZero();
		assertThat(again.skipped()).isEqualTo(r.total());
	}

	@Test
	void 오르막_위_건물과_산이_가림_각도에_들어간다() throws Exception {
		TestBuildings.insertHadan(jdbc);
		// 정남 50m, 높이 10m 건물 → 평지라면 atan(8.8/50) = 10.0°, 땅이 10m 높아 atan(18.8/50) = 20.6°
		TestBuildings.insert(jdbc, "하단동", "2000", HX, HY - 50, 10, "MEASURED");
		slopeToSouth();
		terrain.importAll(p -> {
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
