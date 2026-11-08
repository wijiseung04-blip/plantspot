package com.plantspot.front;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.plantspot.front.BackendClient.Me;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 화면 권한: backend에 로그인한 회원을 물어
 * /my/** 는 회원만(아니면 로그인 화면으로), /admin/** 는 관리자만(아니면 403) 보여준다.
 * 실제 데이터 보호는 backend API가 한다 — 이것은 화면 안내용.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

	public static final String ME = "me";

	private final BackendClient backend;

	public LoginInterceptor(BackendClient backend) {
		this.backend = backend;
	}

	@Override
	public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
		Me me = backend.me(req.getHeader(HttpHeaders.COOKIE)).orElse(null);
		req.setAttribute(ME, me);
		String path = req.getRequestURI();
		if ((path.startsWith("/my/") || path.startsWith("/admin")) && me == null) {
			String next = path + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
			res.sendRedirect("/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
			return false;
		}
		if (path.startsWith("/admin") && !me.isAdmin()) {
			res.sendError(HttpServletResponse.SC_FORBIDDEN);
			return false;
		}
		return true;
	}
}
