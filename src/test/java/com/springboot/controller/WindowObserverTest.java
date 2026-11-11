package com.springboot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.springboot.domain.Role;
import com.springboot.security.LoginMember;
import com.springboot.service.MemberService;
import com.springboot.support.TestBuildings;

/** 창별 관측점: 동 안 세대 위치에 따라 옆 건물의 영향이 달라진다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WindowObserverTest {

	static final double HX = 378731.47;
	static final double HY = 281659.43;

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	LoginMember owner;
	String house;
	String room;

	private static String rect(double x1, double y1, double x2, double y2) {
		return String.format(Locale.ROOT, "MULTIPOLYGON(((%f %f, %f %f, %f %f, %f %f, %f %f)))", x1, y1, x2, y1, x2, y2,
				x1, y2, x1, y1);
	}

	@BeforeEach
	void setUp() throws Exception {
		// 우리 동: 동서 60m × 남북 12m, 높이 18.75m
		TestBuildings.insertHadan(jdbc);
		jdbc.update("update building set footprint_wkt = ? where jibun = '1160-6'",
				rect(HX - 30, HY - 6, HX + 30, HY + 6));
		// 동쪽 끝 바로 남동쪽 건물 (x +35~+47, y −30~−10), 높이 30m
		TestBuildings.insert(jdbc, "하단동", "3000", HX + 41, HY - 20, 30, "MEASURED");
		jdbc.update("update building set footprint_wkt = ? where jibun = '3000'",
				rect(HX + 35, HY - 30, HX + 47, HY - 10));

		owner = new LoginMember(memberService.create("wowner", "password123", "주인", Role.USER));
		house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":1,\"unitPosition\":0.85}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		room = mvc.perform(post(house + "/rooms").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":300},{"x":0,"y":300}],"orientationDeg":0}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":2,\"offset\":100,\"width\":200,\"height\":150,\"sillHeight\":60}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
	}

	private double frontMax() throws Exception {
		String json = mvc.perform(get(room + "/window-obstructions").with(user(owner)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$[0].frontMax").doubleValue();
	}

	@Test
	void 집_응답에_세대_위치와_건물_긴_축() throws Exception {
		mvc.perform(get(house).with(user(owner)))
				.andExpect(jsonPath("$.unitPosition").value(0.85))
				.andExpect(jsonPath("$.buildingAxis").value(90.0));
	}

	@Test
	void 끝_세대는_옆_건물에_크게_가리고_반대쪽_끝은_덜_가린다() throws Exception {
		// 동쪽 끝 세대 남쪽 창: 남동쪽 건물이 약 7m 앞 → 70° 넘게 가림
		double east = frontMax();
		assertThat(east).isGreaterThan(60);
		String eastWinter = mvc.perform(get(room + "/sunlight").param("season", "WINTER_SOLSTICE").param("year", "2026")
				.with(user(owner))).andReturn().getResponse().getContentAsString();

		// 서쪽 끝으로 옮기면 60m 넘게 떨어져 30° 아래
		mvc.perform(put(house).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"집\",\"floor\":1,\"unitPosition\":-0.85}").with(csrf()).with(user(owner)))
				.andExpect(status().isOk());
		double west = frontMax();
		assertThat(west).isLessThan(30);

		// 햇빛도 다시 계산된다: 서쪽 끝 세대 창이 해를 더 오래 받는다
		String westWinter = mvc.perform(get(room + "/sunlight").param("season", "WINTER_SOLSTICE").param("year", "2026")
				.with(user(owner))).andReturn().getResponse().getContentAsString();
		int eastSun = JsonPath.read(eastWinter, "$.windows[0].sunMinutes");
		int westSun = JsonPath.read(westWinter, "$.windows[0].sunMinutes");
		assertThat(westSun).isGreaterThan(eastSun);
	}

	@Test
	void 비회원_간단_예측도_세대_위치를_쓴다() throws Exception {
		String body = "{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"floor\":1,\"windowAzimuth\":180,\"unitPosition\":%s}";
		String east = mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content(String.format(body, "0.85")).with(csrf())).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		String west = mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content(String.format(body, "-0.85")).with(csrf())).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		double e = JsonPath.<Number>read(east, "$.frontObstructionDeg").doubleValue();
		double w = JsonPath.<Number>read(west, "$.frontObstructionDeg").doubleValue();
		assertThat(e).isGreaterThan(w);
	}
}
