package com.plantspot.backend.plant;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 농사로 API HTTP 호출. 키는 application-local.properties 의 api.nongsaro.api-key. */
@Component
public class NongsaroHttpApi implements NongsaroApi {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String baseUrl;
	private final String apiKey;

	public NongsaroHttpApi(@Value("${api.nongsaro.base-url:http://api.nongsaro.go.kr/service/garden}") String baseUrl,
			@Value("${api.nongsaro.api-key:}") String apiKey) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
	}

	public boolean hasKey() {
		return !apiKey.isBlank();
	}

	@Override
	public NongsaroXml.Page list(int pageNo, int numOfRows) {
		return NongsaroXml.parse(get("gardenList", Map.of("pageNo", pageNo, "numOfRows", numOfRows)));
	}

	@Override
	public Map<String, String> detail(String cntntsNo) {
		List<Map<String, String>> items = NongsaroXml.parse(get("gardenDtl", Map.of("cntntsNo", cntntsNo))).items();
		return items.isEmpty() ? Map.of() : items.get(0);
	}

	private byte[] get(String operation, Map<String, Object> params) {
		if (!hasKey()) {
			throw new IllegalStateException("농사로 API 키(api.nongsaro.api-key)가 없습니다.");
		}
		StringBuilder url = new StringBuilder(baseUrl).append('/').append(operation).append("?apiKey=")
				.append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
		params.forEach((k, v) -> url.append('&').append(k).append('=')
				.append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)));
		try {
			HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url.toString()))
					.timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200) {
				throw new IllegalStateException("농사로 HTTP " + res.statusCode());
			}
			return res.body();
		} catch (IOException e) {
			throw new IllegalStateException("농사로 연결 실패: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨", e);
		}
	}
}
