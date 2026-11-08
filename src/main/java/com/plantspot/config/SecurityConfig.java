package com.plantspot.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 권한 구분 [필수: 로그인·회원가입, 회원 / 비회원 / 관리자]
 * <ul>
 * <li>비회원: 첫 화면, 간단 예측 체험, 해·건물·식물 조회(GET)</li>
 * <li>회원: 집·방·창문 등록, 파일, 추천 등 나머지 전부</li>
 * <li>관리자: /admin/**, /api/admin/**</li>
 * </ul>
 * /api/** 는 로그인 화면으로 보내지 않고 401/403 JSON을 돌려준다.
 * CSRF는 켜 두고, 화면의 fetch 요청은 meta 태그의 토큰을 헤더로 보낸다(static/js/api.js).
 */
@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		RequestMatcher api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/login", "/signup", "/try", "/plants", "/plants/**", "/error",
						"/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/sun/**", "/api/buildings/**", "/api/plants/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/preview").permitAll()
				.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("loginId")
				.defaultSuccessUrl("/", false)
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/"))
			.exceptionHandling(ex -> ex
				.defaultAuthenticationEntryPointFor(jsonEntryPoint(), api)
				.defaultAccessDeniedHandlerFor(jsonAccessDenied(), api));
		return http.build();
	}

	private static AuthenticationEntryPoint jsonEntryPoint() {
		return (request, response, e) -> writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
				"로그인이 필요합니다.", request.getRequestURI());
	}

	private static AccessDeniedHandler jsonAccessDenied() {
		return (request, response, e) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
				"권한이 없습니다.", request.getRequestURI());
	}

	private static void writeJson(HttpServletResponse response, int status, String error, String message, String path)
			throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message
				+ "\",\"path\":\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
	}
}
