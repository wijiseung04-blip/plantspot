package com.plantspot.frontend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.member.MemberService;
import com.plantspot.backend.member.Role;

/** 화면 권한: 비회원 화면은 보이고, 회원 화면은 로그인으로, 관리자 화면은 관리자만. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PageTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

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
	}

	@Test
	void 로그인하면_머리글에_닉네임과_회원_메뉴() throws Exception {
		LoginMember me = new LoginMember(memberService.create("page_user", "password123", "화면회원", Role.USER));
		mvc.perform(get("/my/houses").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("화면회원")))
				.andExpect(content().string(Matchers.containsString("/my/plants")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/admin\""))));
	}

	@Test
	void 관리자_화면은_관리자만() throws Exception {
		LoginMember normal = new LoginMember(memberService.create("page_user2", "password123", "회원", Role.USER));
		LoginMember admin = new LoginMember(memberService.create("page_admin", "password123", "관리자", Role.ADMIN));
		mvc.perform(get("/admin/members").with(user(normal))).andExpect(status().isForbidden());
		mvc.perform(get("/admin/members").with(user(admin))).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("href=\"/admin\"")));
	}

	@Test
	void 정적_파일() throws Exception {
		mvc.perform(get("/js/api.js")).andExpect(status().isOk());
		mvc.perform(get("/editor/solar.js")).andExpect(status().isOk());
	}
}
