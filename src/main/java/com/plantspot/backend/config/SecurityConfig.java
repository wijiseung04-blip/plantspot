package com.plantspot.backend.config;

import java.net.URLEncoder;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 권한 구분 [필수: 로그인·회원가입, 회원 / 비회원 / 관리자]
 * <ul>
 * <li>비회원: 첫 화면·로그인·가입·간단 예측·식물 검색·에디터 화면, 해·건물·식물·주소 조회 API, 간단 예측 API</li>
 * <li>회원: /my/** 화면, 나머지 /api/**</li>
 * <li>관리자: /admin/** 화면, /api/admin/**</li>
 * </ul>
 * 로그인은 세션(쿠키). /api/** 는 401·403 JSON, 화면은 로그인 화면으로 보내거나 403 오류 화면.
 * CSRF: XSRF-TOKEN 쿠키 값을 헤더로 보내야 한다 (static/js/api.js 가 처리).
 */
@Configuration
@ConditionalOnWebApplication // 가져오기 프로그램(웹 서버 없이 실행)에서는 쓰지 않는다
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository)
			throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				// 화면 (frontend)
				.requestMatchers("/", "/login", "/signup", "/try", "/plants", "/plants/**", "/editor", "/error",
						"/css/**", "/js/**", "/editor/**", "/favicon.ico").permitAll()
				.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
				.requestMatchers("/my/**").authenticated()
				// API (backend)
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/sun/**", "/api/buildings/**", "/api/plants/**", "/api/address/**").permitAll()
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
				.authenticationEntryPoint((req, res, e) -> {
					if (isApi(req)) {
						writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "로그인이 필요합니다.",
								req.getRequestURI());
					} else {
						// 화면: 로그인 후 돌아올 주소를 붙여 로그인 화면으로
						String next = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
						res.sendRedirect("/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
					}
				})
				.accessDeniedHandler((req, res, e) -> {
					if (isApi(req)) {
						writeJson(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "권한이 없습니다.", req.getRequestURI());
					} else {
						res.sendError(HttpServletResponse.SC_FORBIDDEN);
					}
				}));
		return http.build();
	}

	private static boolean isApi(HttpServletRequest req) {
		return req.getRequestURI().startsWith("/api/");
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
