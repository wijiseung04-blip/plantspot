package com.springboot.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

import com.springboot.domain.Role;
import com.springboot.security.LoginMember;
import com.springboot.service.MemberService;
import com.springboot.support.TestBuildings;

/** 건물 층수보다 높은 층은 등록·수정·간단 예측 모두 400 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HouseFloorTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	@jakarta.persistence.PersistenceContext
	jakarta.persistence.EntityManager em;

	LoginMember owner;

	@BeforeEach
	void setUp() {
		TestBuildings.insertHadan(jdbc); // 지상 6층
		// 층수·높이 정보가 없는 작은 건물 (높이 기본값 3.5m)
		TestBuildings.insert(jdbc, "하단동", "500", 378900, 281700, 3.5, "DEFAULT");
		jdbc.update("update building set ground_floors = null where jibun = '500'");
		owner = new LoginMember(memberService.create("fowner", "password123", "주인", Role.USER));
	}

	private String house(String jibun, int floor) {
		return "{\"dong\":\"하단동\",\"jibun\":\"" + jibun + "\",\"name\":\"집\",\"floor\":" + floor + "}";
	}

	@Test
	void 건물_층수보다_높은_층은_등록할_수_없다() throws Exception {
		mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON).content(house("1160-6", 7))
				.with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("지상 6층")));
		mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON).content(house("1160-6", 6))
				.with(csrf()).with(user(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.maxFloor").value(6))
				.andExpect(jsonPath("$.floorWarning").doesNotExist());
	}

	@Test
	void 수정할_때도_막는다() throws Exception {
		String location = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content(house("1160-6", 3)).with(csrf()).with(user(owner))).andReturn().getResponse()
				.getHeader("Location");
		mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"집\",\"floor\":20}")
				.with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("6 이하")));
		mvc.perform(get(location).with(user(owner))).andExpect(jsonPath("$.floor").value(3));
	}

	@Test
	void 층수를_모르는_건물은_막지_않고_경고만() throws Exception {
		mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON).content(house("500", 12))
				.with(csrf()).with(user(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.maxFloor").doesNotExist())
				.andExpect(jsonPath("$.floorWarning", Matchers.containsString("층수 정보가 없어")));
	}

	@Test
	void 이미_범위를_넘은_집은_경고를_붙인다() throws Exception {
		String location = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content(house("1160-6", 5)).with(csrf()).with(user(owner))).andReturn().getResponse()
				.getHeader("Location");
		// 건물 데이터를 다시 가져와 층수가 줄어든 경우
		jdbc.update("update building set ground_floors = 4 where jibun = '1160-6'");
		em.clear(); // 이미 읽어 둔 건물 엔티티 버리기
		mvc.perform(get(location).with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.floorWarning", Matchers.containsString("건물 범위를 넘습니다")));
	}

	@Test
	void 비회원_간단_예측도_막는다() throws Exception {
		mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"floor\":9,\"windowAzimuth\":180}").with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("지상 6층")));
		mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"floor\":6,\"windowAzimuth\":180}").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxFloor").value(6));
	}
}
