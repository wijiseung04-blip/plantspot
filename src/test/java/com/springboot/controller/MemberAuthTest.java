package com.springboot.controller;

import com.springboot.domain.Member;
import com.springboot.domain.Role;
import com.springboot.repository.MemberRepository;
import com.springboot.security.LoginMember;
import com.springboot.service.MemberService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAuthTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberRepository repository;

	@Autowired
	MemberService memberService;

	private static String signupJson(String id, String pw, String confirm, String nick) {
		return "{\"loginId\":\"" + id + "\",\"password\":\"" + pw + "\",\"passwordConfirm\":\"" + confirm
				+ "\",\"nickname\":\"" + nick + "\"}";
	}

	@Test
	void 회원가입하면_비밀번호는_해시로_저장되고_로그인하면_세션이_생긴다() throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson("plant_lover", "password123", "password123", "식물러")).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));
		Member saved = repository.findByLoginId("plant_lover").orElseThrow();
		assertThat(saved.getPassword()).isNotEqualTo("password123").startsWith("$2");

		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"plant_lover\",\"password\":\"password123\"}").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("식물러"));
		mvc.perform(get("/api/members/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("plant_lover"));

		mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void 비밀번호가_틀리면_401() throws Exception {
		memberService.create("wrong_pw", "password123", "회원", Role.USER);
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"wrong_pw\",\"password\":\"nope-nope\"}").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	void 입력값이_잘못되면_400_메시지() throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson("AB", "short", "different", "")).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("아이디는 영문 소문자")))
				.andExpect(jsonPath("$.message", Matchers.containsString("비밀번호 확인이 일치하지 않습니다.")));
	}

	@Test
	void 중복_아이디는_409() throws Exception {
		memberService.create("dup_user", "password123", "첫번째", Role.USER);
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson("dup_user", "password123", "password123", "두번째")).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void CSRF_토큰은_쿠키로_받는다() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName").isNotEmpty())
				.andExpect(jsonPath("$.token").isNotEmpty())
				;
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson("nocsrf", "password123", "password123", "닉네임")))
				.andExpect(status().isForbidden());
	}

	@Test
	void 비회원은_공개_API는_쓰고_회원_API는_401() throws Exception {
		mvc.perform(get("/api/sun").param("dateTime", "2026-12-21T12:30")).andExpect(status().isOk());
		mvc.perform(get("/api/members/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
		mvc.perform(get("/api/houses")).andExpect(status().isUnauthorized());
	}

	@Test
	void 관리자_API는_관리자만() throws Exception {
		Member normal = memberService.create("normal1", "password123", "회원", Role.USER);
		Member admin = memberService.create("admin1", "password123", "관리자", Role.ADMIN);
		mvc.perform(get("/api/admin/members").with(user(new LoginMember(normal)))).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/members").with(user(new LoginMember(admin))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.loginId=='admin1')].roleLabel").value("관리자"));
	}

	@Test
	void 관리자가_권한을_바꾼다_마지막_관리자는_못_내린다() throws Exception {
		Member admin = memberService.create("admin2", "password123", "관리자", Role.ADMIN);
		Member normal = memberService.create("normal2", "password123", "회원", Role.USER);
		mvc.perform(put("/api/admin/members/{id}/role", normal.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"ADMIN\"}").with(csrf()).with(user(new LoginMember(admin))))
				.andExpect(jsonPath("$.role").value("ADMIN"));
		memberService.changeRole(normal.getId(), Role.USER);
		mvc.perform(put("/api/admin/members/{id}/role", admin.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"USER\"}").with(csrf()).with(user(new LoginMember(admin))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("마지막 관리자는 권한을 내릴 수 없습니다."));
	}
}
