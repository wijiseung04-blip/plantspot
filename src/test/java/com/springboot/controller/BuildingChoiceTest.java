package com.springboot.controller;

import com.springboot.service.BuildingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.client.AddressApi;
import com.springboot.security.LoginMember;
import com.springboot.service.MemberService;
import com.springboot.domain.Role;
import com.springboot.support.TestBuildings;

/** 아파트 단지처럼 한 지번에 건물이 여러 개일 때: 가장 높은 건물이 기본, 고른 건물(buildingId)을 정확히 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuildingChoiceTest {

	@MockitoBean
	AddressApi addressApi;

	@Autowired
	MockMvc mvc;

	@Autowired
	BuildingService service;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	long guardhouse;
	long dong101;
	long dong102;

	private long insert(String dongLabel, double x, double height, double area) {
		TestBuildings.insert(jdbc, "신평동", "400", x, 281000, height, "MEASURED");
		long id = jdbc.queryForObject("SELECT max(id) FROM building", Long.class);
		jdbc.update("UPDATE building SET dong_label = ?, area = ? WHERE id = ?", dongLabel, area, id);
		return id;
	}

	@BeforeEach
	void setUp() {
		guardhouse = insert(null, 378000, 3.5, 600); // 넓지만 낮은 관리동
		dong101 = insert("101동", 378050, 45, 400);
		dong102 = insert("102동", 378100, 42, 400);
	}

	@Test
	void 고르지_않으면_가장_높은_건물() {
		assertThat(service.choose("신평동", "400", null).getId()).isEqualTo(dong101);
	}

	@Test
	void 고른_건물을_쓰고_다른_지번_건물은_거절() {
		assertThat(service.choose("신평동", "400", dong102).getId()).isEqualTo(dong102);
		TestBuildings.insertHadan(jdbc);
		long other = jdbc.queryForObject("SELECT id FROM building WHERE jibun = '1160-6'", Long.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.choose("신평동", "400", other))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 지번의_건물_목록에_동명이_나온다() throws Exception {
		mvc.perform(get("/api/buildings").param("dong", "신평동").param("jibun", "400"))
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[?(@.dongLabel=='102동')].height").value(42.0));
	}

	@Test
	void 집_등록과_간단_예측에서_고른_건물을_쓴다() throws Exception {
		LoginMember me = new LoginMember(memberService.create("bchoice", "password123", "회원", Role.USER));
		mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"신평동\",\"jibun\":\"400\",\"name\":\"집\",\"floor\":5,\"buildingId\":" + dong102 + "}")
				.with(csrf()).with(user(me)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.buildingHeight").value(42.0));
		mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"신평동\",\"jibun\":\"400\",\"name\":\"집2\",\"floor\":5}")
				.with(csrf()).with(user(me)))
				.andExpect(jsonPath("$.buildingHeight").value(45.0));
		mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"신평동\",\"jibun\":\"400\",\"floor\":3,\"windowAzimuth\":180,\"buildingId\":" + guardhouse + "}")
				.with(csrf()))
				.andExpect(jsonPath("$.buildingHeight").value(3.5));
	}

	@Test
	void 주소_검색은_같은_결과를_하나로_합친다() throws Exception {
		AddressApi.Candidate same = new AddressApi.Candidate("부산광역시 사하구 테스트로 170", "신평동 400", "49419",
				"테스트아파트", null, 378000, 281000);
		AddressApi.Candidate d101 = new AddressApi.Candidate("부산광역시 사하구 테스트로 170", "신평동 400", "49419",
				"테스트아파트", "101동", 378050, 281000);
		when(addressApi.hasKey()).thenReturn(true);
		when(addressApi.search(anyString(), eq("road"))).thenReturn(List.of(same, same, same, d101, d101));
		mvc.perform(get("/api/address/search").param("query", "테스트로 170"))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].buildingDetail").value("101동"));
	}
}
