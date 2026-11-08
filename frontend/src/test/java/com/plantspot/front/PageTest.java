package com.plantspot.front;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** backend 없이: 비회원 화면은 보이고, 회원 화면은 로그인으로 보내고, API는 502 안내. */
@SpringBootTest
@AutoConfigureMockMvc
class PageTest {

	@Autowired
	MockMvc mvc;

	@Test
	void 비회원_화면이_렌더링된다() throws Exception {
		for (String path : new String[] { "/", "/login", "/signup", "/try", "/plants", "/plants/1", "/editor" }) {
			mvc.perform(get(path)).andExpect(status().isOk())
					.andExpect(content().string(Matchers.containsString("plantspot")));
		}
		mvc.perform(get("/")).andExpect(content().string(Matchers.containsString("로그인")));
	}

	@Test
	void 회원_화면은_로그인으로_보낸다() throws Exception {
		mvc.perform(get("/my/rooms/3/sunlight")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?next=%2Fmy%2Frooms%2F3%2Fsunlight"));
		mvc.perform(get("/admin/members")).andExpect(status().is3xxRedirection());
	}

	@Test
	void backend가_꺼져_있으면_API는_502_안내() throws Exception {
		mvc.perform(get("/api/sun").param("dateTime", "2026-12-21T12:30"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message", Matchers.containsString("backend")));
	}

	@Test
	void 정적_파일() throws Exception {
		mvc.perform(get("/js/api.js")).andExpect(status().isOk());
		mvc.perform(get("/editor/solar.js")).andExpect(status().isOk());
	}
}
