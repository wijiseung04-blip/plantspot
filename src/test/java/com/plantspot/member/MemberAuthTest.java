package com.plantspot.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

	@Test
	void 회원가입하면_비밀번호는_해시로_저장되고_로그인할_수_있다() throws Exception {
		mvc.perform(post("/signup").with(csrf())
				.param("loginId", "plant_lover").param("password", "password123")
				.param("passwordConfirm", "password123").param("nickname", "식물러"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));

		Member saved = repository.findByLoginId("plant_lover").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getPassword()).isNotEqualTo("password123").startsWith("$2");

		mvc.perform(formLogin("/login").user("loginId", "plant_lover").password("password123"))
				.andExpect(authenticated().withRoles("USER"));
		mvc.perform(formLogin("/login").user("loginId", "plant_lover").password("wrong-password"))
				.andExpect(unauthenticated());
	}

	@Test
	void 입력값이_잘못되면_가입_화면에_오류를_보여준다() throws Exception {
		mvc.perform(post("/signup").with(csrf())
				.param("loginId", "AB").param("password", "short")
				.param("passwordConfirm", "different").param("nickname", ""))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "loginId", "password", "nickname",
						"passwordMatching"));
	}

	@Test
	void 중복_아이디는_가입되지_않는다() throws Exception {
		memberService.create("dup_user", "password123", "첫번째", Role.USER);
		mvc.perform(post("/signup").with(csrf())
				.param("loginId", "dup_user").param("password", "password123")
				.param("passwordConfirm", "password123").param("nickname", "두번째"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("form", "loginId", "duplicate"));
	}

	@Test
	void 비회원은_공개_API는_쓰고_회원_API는_401() throws Exception {
		mvc.perform(get("/api/sun").param("dateTime", "2026-12-21T12:30")).andExpect(status().isOk());
		mvc.perform(get("/api/members/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	void 비회원이_회원_화면에_가면_로그인_화면으로() throws Exception {
		mvc.perform(get("/my/houses").accept(org.springframework.http.MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void 내_정보() throws Exception {
		Member m = memberService.create("me_user", "password123", "나", Role.USER);
		mvc.perform(get("/api/members/me").with(user(new LoginMember(m))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("me_user"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void 관리자_화면은_관리자만() throws Exception {
		Member normal = memberService.create("normal1", "password123", "회원", Role.USER);
		Member admin = memberService.create("admin1", "password123", "관리자", Role.ADMIN);
		mvc.perform(get("/admin/members").with(user(new LoginMember(normal)))).andExpect(status().isForbidden());
		mvc.perform(get("/admin/members").with(user(new LoginMember(admin)))).andExpect(status().isOk());
	}

	@Test
	void 관리자가_권한을_바꾼다_마지막_관리자는_못_내린다() throws Exception {
		Member admin = memberService.create("admin2", "password123", "관리자", Role.ADMIN);
		Member normal = memberService.create("normal2", "password123", "회원", Role.USER);

		mvc.perform(post("/admin/members/{id}/role", normal.getId()).param("role", "ADMIN").with(csrf())
				.with(user(new LoginMember(admin)))).andExpect(status().is3xxRedirection());
		assertThat(repository.findById(normal.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);

		memberService.changeRole(normal.getId(), Role.USER);
		mvc.perform(post("/admin/members/{id}/role", admin.getId()).param("role", "USER").with(csrf())
				.with(user(new LoginMember(admin)))).andExpect(status().is3xxRedirection());
		assertThat(repository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void CSRF_토큰_없는_POST는_거절() throws Exception {
		mvc.perform(post("/signup").param("loginId", "nocsrf")).andExpect(status().isForbidden());
	}

	@Test
	void 화면이_렌더링된다() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(get("/login")).andExpect(status().isOk());
		mvc.perform(get("/signup")).andExpect(status().isOk());
	}
}
