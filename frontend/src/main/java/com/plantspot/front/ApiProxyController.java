package com.plantspot.front;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 브라우저의 /api/** 요청을 backend로 그대로 전달하고 응답(쿠키·파일 포함)을 그대로 돌려준다.
 * 덕분에 브라우저는 frontend 주소 하나만 보므로 로그인 세션·CSRF 쿠키가 그대로 동작한다.
 */
@RestController
public class ApiProxyController {

	private static final Logger log = LoggerFactory.getLogger(ApiProxyController.class);

	/** HttpClient가 직접 정하는 헤더, 연결 관련 헤더는 넘기지 않는다 */
	private static final Set<String> SKIP_REQUEST = Set.of("host", "connection", "content-length", "expect", "upgrade",
			"transfer-encoding", "keep-alive", "te", "trailer", "proxy-connection", "http2-settings");
	private static final Set<String> SKIP_RESPONSE = Set.of("transfer-encoding", "connection", "keep-alive",
			"content-length");

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER).build();
	private final String backendUrl;

	public ApiProxyController(@Value("${backend.url}") String backendUrl) {
		this.backendUrl = backendUrl.replaceAll("/+$", "");
	}

	@RequestMapping("/api/**")
	public void proxy(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String target = backendUrl + req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
		byte[] body = req.getInputStream().readAllBytes();
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(target))
				.timeout(Duration.ofMinutes(5))
				.method(req.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofByteArray(body));
		for (String name : Collections.list(req.getHeaderNames())) {
			if (SKIP_REQUEST.contains(name.toLowerCase(Locale.ROOT))) {
				continue;
			}
			for (String value : Collections.list(req.getHeaders(name))) {
				b.header(name, value);
			}
		}
		b.header("X-Forwarded-For", req.getRemoteAddr());

		HttpResponse<InputStream> r;
		try {
			r = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (ConnectException e) {
			log.warn("backend 연결 실패: {}", backendUrl);
			writeError(res, 502, "backend 서버(" + backendUrl + ")에 연결할 수 없습니다. backend를 먼저 실행하세요.");
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			writeError(res, 503, "요청이 중단되었습니다.");
			return;
		}
		res.setStatus(r.statusCode());
		r.headers().map().forEach((name, values) -> {
			if (!SKIP_RESPONSE.contains(name.toLowerCase(Locale.ROOT)) && !name.startsWith(":")) {
				values.forEach(v -> res.addHeader(name, v));
			}
		});
		try (InputStream in = r.body(); OutputStream out = res.getOutputStream()) {
			in.transferTo(out);
		}
	}

	private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
		res.setStatus(status);
		res.setContentType("application/json");
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
	}
}
