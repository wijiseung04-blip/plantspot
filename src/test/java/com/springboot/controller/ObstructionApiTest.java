package com.springboot.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.security.LoginMember;
import com.springboot.service.MemberService;
import com.springboot.domain.Role;
import com.springboot.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObstructionApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	LoginMember owner;
	String house;

	@BeforeEach
	void setUp() throws Exception {
		TestBuildings.insertHadan(jdbc);
		// 정남 50m, 높이 51.2m → 1층(눈 1.2m)에서 45°, 외곽선 없음
		TestBuildings.insert(jdbc, "하단동", "2000", 378731.47, 281609.43, 51.2, "MEASURED");
		owner = new LoginMember(memberService.create("oowner", "password123", "주인", Role.USER));
		house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":1}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
	}

	@Test
	void 처음_조회하면_건물_데이터로_계산한다() throws Exception {
		mvc.perform(get(house + "/obstructions").with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(72))
				.andExpect(jsonPath("$[36].azimuth").value(180))
				.andExpect(jsonPath("$[36].angle").value(45.0))
				.andExpect(jsonPath("$[36].source").value("BUILDING"))
				.andExpect(jsonPath("$[36].distance").value(50.0))
				.andExpect(jsonPath("$[0].angle").value(0.0));
	}

	@Test
	void 층을_올리면_가림_각도가_낮아진다() throws Exception {
		mvc.perform(get(house + "/obstructions").with(user(owner))).andExpect(jsonPath("$[36].angle").value(45.0));
		// 11층: 눈 높이 31.2m → atan(20/50) = 21.8°
		mvc.perform(put(house).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"집\",\"floor\":11}")
				.with(csrf()).with(user(owner))).andExpect(status().isOk());
		mvc.perform(get(house + "/obstructions").with(user(owner))).andExpect(jsonPath("$[36].angle").value(21.8));
	}

	@Test
	void 실측값은_건물_데이터를_덮어쓰고_지우면_돌아온다() throws Exception {
		mvc.perform(put(house + "/obstructions/measured").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"azimuthDeg\":182,\"angleDeg\":52.5}]").with(csrf()).with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[36].angle").value(52.5))
				.andExpect(jsonPath("$[36].buildingAngle").value(45.0))
				.andExpect(jsonPath("$[36].source").value("SENSOR"));
		mvc.perform(delete(house + "/obstructions/measured").with(csrf()).with(user(owner)))
				.andExpect(jsonPath("$[36].angle").value(45.0))
				.andExpect(jsonPath("$[36].sensorAngle").doesNotExist());
	}

	@Test
	void 실측값_범위_검사() throws Exception {
		mvc.perform(put(house + "/obstructions/measured").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"azimuthDeg\":182,\"angleDeg\":95}]").with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("0~90")));
	}

	@Test
	void 집을_지우면_가림_각도도_지운다() throws Exception {
		mvc.perform(get(house + "/obstructions").with(user(owner))).andExpect(status().isOk());
		mvc.perform(delete(house).with(csrf()).with(user(owner))).andExpect(status().isNoContent());
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select count(*) from obstruction_angle", Integer.class)).isZero();
	}
}
