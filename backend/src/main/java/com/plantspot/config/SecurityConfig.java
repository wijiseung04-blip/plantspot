package com.plantspot.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * backend 권한 구분 [필수: 로그인·회원가입, 회원 / 비회원 / 관리자]
 * <ul>
 * <li>비회원: 로그인·가입, 해·건물·식물 조회(GET), 간단 예측(POST /api/preview)</li>
 * <li>회원: 집·방·창문, 파일, 추천 등 나머지 /api/** 전부</li>
 * <li>관리자: /api/admin/**</li>
 * </ul>
 * 로그인은 세션(쿠키 JSESSIONID). 화면(frontend)이 /api/** 를 그대로 전달하므로 브라우저 입장에서는 같은 주소다.
 * CSRF: XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 한다 (frontend의 api.js가 처리).
 */
@Configuration
@ConditionalOnWebApplication // 가져오기 프로그램(웹 서버 없이 실행)에서는 쓰지 않는다
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository)
			throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**", "/error").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/sun/**", "/api/buildings/**", "/api/plants/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/preview").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.securityContext(sc -> sc.securityContextRepository(contextRepository))
			.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((req, res, e) -> writeJson(res, HttpServletResponse.SC_UNAUTHORIZED,
						"Unauthorized", "로그인이 필요합니다.", req.getRequestURI()))
				.accessDeniedHandler((req, res, e) -> writeJson(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
						"권한이 없습니다.", req.getRequestURI())));
		return http.build();
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
