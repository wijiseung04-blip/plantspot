package com.plantspot.backend.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.member.MemberService;
import com.plantspot.backend.member.Role;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlantApiTest {

	@MockitoBean
	NongsaroApi nongsaro;

	@Autowired
	MockMvc mvc;

	@Autowired
	PlantImportService importService;

	@Autowired
	PlantSpeciesRepository repository;

	@Autowired
	MemberService memberService;

	LoginMember admin;
	LoginMember normal;

	@BeforeEach
	void setUp() {
		admin = new LoginMember(memberService.create("padmin", "password123", "관리자", Role.ADMIN));
		normal = new LoginMember(memberService.create("puser", "password123", "회원", Role.USER));
		when(nongsaro.list(eq(1), anyInt())).thenReturn(NongsaroXml.parse(NongsaroXmlTest.resource("list-page1.xml")));
		when(nongsaro.list(eq(2), anyInt())).thenReturn(NongsaroXml.parse(NongsaroXmlTest.resource("list-page2.xml")));
		when(nongsaro.detail("90001")).thenReturn(NongsaroXml.parse(NongsaroXmlTest.resource("detail-90001.xml")).items().get(0));
		when(nongsaro.detail("90002")).thenReturn(Map.of("lighttdemanddoCode", "055003", "winterLwetTpCode", "057001"));
		when(nongsaro.detail("90003")).thenThrow(new IllegalStateException("시간 초과"));
	}

	@Test
	void 목록_전체와_상세를_모아_저장하고_실패는_건너뛴다() {
		PlantImportService.ImportResult r = importService.importAll(p -> {
		});
		assertThat(r.total()).isEqualTo(3);
		assertThat(r.created()).isEqualTo(2);
		assertThat(r.failed()).isEqualTo(1);
		PlantSpecies vine = repository.findByCntntsNo("90001").orElseThrow();
		assertThat(vine.getMinLux()).isEqualTo(300);
		assertThat(vine.getMaxLux()).isEqualTo(1500);
		assertThat(vine.getWinterMinTemp()).isEqualTo(7);

		// 다시 가져오면 갱신
		assertThat(importService.importAll(p -> {
		}).updated()).isEqualTo(2);
	}

	@Test
	void 관리자가_고친_변환값은_다시_가져와도_유지된다() throws Exception {
		importService.importAll(p -> {
		});
		Long id = repository.findByCntntsNo("90001").orElseThrow().getId();
		mvc.perform(put("/api/admin/plants/{id}/conversion", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"minLux\":500,\"maxLux\":1200,\"minDirectMinutes\":0,\"winterMinTemp\":10}")
				.with(csrf()).with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adjusted").value(true));
		importService.importAll(p -> {
		});
		assertThat(repository.findById(id).orElseThrow().getMinLux()).isEqualTo(500);

		mvc.perform(post("/api/admin/plants/{id}/conversion/reset", id).with(csrf()).with(user(admin)))
				.andExpect(jsonPath("$.minLux").value(300))
				.andExpect(jsonPath("$.adjusted").value(false));
	}

	@Test
	void 비회원도_검색할_수_있다() throws Exception {
		importService.importAll(p -> {
		});
		mvc.perform(get("/api/plants").param("keyword", "덩굴"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].name").value("테스트덩굴"));
		mvc.perform(get("/api/plants").param("light", "055003"))
				.andExpect(jsonPath("$.items[0].name").value("테스트선인장"));
		mvc.perform(get("/api/plants").param("keyword", "vine"))
				.andExpect(jsonPath("$.total").value(1));
		Long id = repository.findByCntntsNo("90001").orElseThrow().getId();
		mvc.perform(get("/api/plants/{id}", id)).andExpect(status().isOk());
		mvc.perform(get("/api/plants/{id}", 99999)).andExpect(status().isNotFound());
	}

	@Test
	void 회원은_변환표를_고칠_수_없다() throws Exception {
		mvc.perform(post("/api/admin/plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"몬스테라\",\"minLux\":800,\"maxLux\":1500,\"minDirectMinutes\":0}")
				.with(csrf()).with(user(normal)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/plants/import").with(user(normal))).andExpect(status().isForbidden());
	}

	@Test
	void 직접_추가와_값_검사() throws Exception {
		mvc.perform(post("/api/admin/plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"직접식물\",\"minLux\":800,\"maxLux\":1500,\"minDirectMinutes\":0}")
				.with(csrf()).with(user(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.source").value("MANUAL"));
		mvc.perform(post("/api/admin/plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"이상\",\"minLux\":2000,\"maxLux\":1500,\"minDirectMinutes\":0}")
				.with(csrf()).with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("최소 lux")));
		mvc.perform(get("/api/admin/plants/import").with(user(admin))).andExpect(status().isOk());
	}
}
