package com.plantspot.sunlight;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.plantspot.member.LoginMember;
import com.plantspot.member.MemberService;
import com.plantspot.member.Role;
import com.plantspot.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SunlightApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	LoginMember owner;
	String house;
	String room;

	@BeforeEach
	void setUp() throws Exception {
		TestBuildings.insertHadan(jdbc);
		owner = new LoginMember(memberService.create("sowner", "password123", "주인", Role.USER));
		house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":5}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		room = mvc.perform(post(house + "/rooms").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":300},{"x":0,"y":300}],"orientationDeg":0}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":2,\"offset\":100,\"width\":200,\"height\":150,\"sillHeight\":60}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
	}

	private int storedCells() {
		return jdbc.queryForObject("select count(*) from sunlight_cell", Integer.class);
	}

	@Test
	void 계절_결과는_처음_조회할_때_계산해서_저장한다() throws Exception {
		mvc.perform(get(room + "/sunlight").param("season", "WINTER_SOLSTICE").param("year", "2026").with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.label").value("동지"))
				.andExpect(jsonPath("$.date").value("2026-12-22"))
				.andExpect(jsonPath("$.cells.length()").value(192))
				.andExpect(jsonPath("$.windows[0].direction").value("남향"))
				.andExpect(jsonPath("$.windows[0].sunMinutes", Matchers.greaterThan(300)))
				.andExpect(jsonPath("$.maxDirectMinutes", Matchers.greaterThan(60)));
		assertThat(storedCells()).isEqualTo(192);
		// 두 번째는 저장된 값 (computedAt 그대로)
		mvc.perform(get(room + "/sunlight").param("season", "WINTER_SOLSTICE").param("year", "2026").with(user(owner)))
				.andExpect(jsonPath("$.windows[0].sunMinutes", Matchers.greaterThan(300)));
		assertThat(storedCells()).isEqualTo(192);
	}

	@Test
	void 사계절_한번에_계산() throws Exception {
		mvc.perform(post(room + "/sunlight/calculate").param("year", "2026").with(csrf()).with(user(owner)))
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[1].label").value("하지"));
		assertThat(storedCells()).isEqualTo(192 * 4);
	}

	@Test
	void 날짜를_주면_저장하지_않고_계산() throws Exception {
		mvc.perform(get(room + "/sunlight").param("date", "2026-11-08").with(user(owner)))
				.andExpect(jsonPath("$.label").value("2026-11-08"))
				.andExpect(jsonPath("$.season").doesNotExist());
		assertThat(storedCells()).isZero();
	}

	@Test
	void 창문을_바꾸면_저장된_결과를_지운다() throws Exception {
		mvc.perform(get(room + "/sunlight").param("season", "SUMMER_SOLSTICE").with(user(owner)));
		assertThat(storedCells()).isPositive();
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":1,\"offset\":50,\"width\":100,\"height\":120,\"sillHeight\":80}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
		assertThat(storedCells()).isZero();
	}

	@Test
	void 실측_가림_각도를_넣으면_보정_전후가_달라진다() throws Exception {
		// 남쪽 전체를 40°로 막는 실측값 → 동지에는 직사광 없음
		StringBuilder body = new StringBuilder("[");
		for (int a = 90; a <= 270; a += 5) {
			body.append(a == 90 ? "" : ",").append("{\"azimuthDeg\":").append(a).append(",\"angleDeg\":40}");
		}
		body.append("]");
		mvc.perform(put(house + "/obstructions/measured").contentType(MediaType.APPLICATION_JSON)
				.content(body.toString()).with(csrf()).with(user(owner))).andExpect(status().isOk());
		mvc.perform(get(room + "/sunlight").param("date", "2026-12-22").with(user(owner)))
				.andExpect(jsonPath("$.calibrated").value(true))
				.andExpect(jsonPath("$.maxDirectMinutes").value(0));
		mvc.perform(get(room + "/sunlight").param("date", "2026-12-22").param("calibrated", "false").with(user(owner)))
				.andExpect(jsonPath("$.calibrated").value(false))
				.andExpect(jsonPath("$.maxDirectMinutes", Matchers.greaterThan(60)));
	}

	@Test
	void 방을_지우면_햇빛_결과도_지운다() throws Exception {
		mvc.perform(get(room + "/sunlight").param("season", "SPRING_EQUINOX").with(user(owner)));
		mvc.perform(delete(room).with(csrf()).with(user(owner))).andExpect(status().isNoContent());
		assertThat(storedCells()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from sunlight_summary", Integer.class)).isZero();
	}

}
