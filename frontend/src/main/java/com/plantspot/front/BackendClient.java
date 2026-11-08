package com.plantspot.front;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** 화면 서버가 backend에 직접 묻는 일: 지금 로그인한 회원이 누구인가 (브라우저 쿠키를 그대로 실어 보낸다). */
@Component
public class BackendClient {

	private static final Logger log = LoggerFactory.getLogger(BackendClient.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final String backendUrl;

	public BackendClient(@Value("${backend.url}") String backendUrl) {
		this.backendUrl = backendUrl.replaceAll("/+$", "");
	}

	/** 로그인한 회원. role: USER / ADMIN */
	public record Me(Long id, String loginId, String nickname, String role) {

		public boolean isAdmin() {
			return "ADMIN".equals(role);
		}
	}

	/** 로그인 안 했거나 backend가 꺼져 있으면 비어 있음. */
	public Optional<Me> me(String cookieHeader) {
		if (cookieHeader == null || cookieHeader.isBlank()) {
			return Optional.empty();
		}
		try {
			HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/members/me"))
					.timeout(Duration.ofSeconds(5)).header("Cookie", cookieHeader).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				return Optional.empty();
			}
			return Optional.of(JSON.readValue(r.body(), Me.class));
		} catch (Exception e) {
			log.debug("backend 회원 확인 실패: {}", e.getMessage());
			return Optional.empty();
		}
	}
}
