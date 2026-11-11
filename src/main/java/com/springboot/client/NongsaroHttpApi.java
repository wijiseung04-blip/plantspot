package com.springboot.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

	/** 받은 응답 원문을 남기는 폴더 (형식 확인·오류 진단용, 키는 들어가지 않음) */
	private final Path sampleDir;
	private final Set<String> saved = ConcurrentHashMap.newKeySet();

	public NongsaroHttpApi(@Value("${api.nongsaro.base-url:http://api.nongsaro.go.kr/service/garden}") String baseUrl,
			@Value("${api.nongsaro.api-key:}") String apiKey,
			@Value("${api.nongsaro.sample-dir:data/nongsaro-sample}") String sampleDir) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.sampleDir = Path.of(sampleDir);
	}

	public boolean hasKey() {
		return !apiKey.isBlank();
	}

	@Override
	public NongsaroXml.Page list(int pageNo, int numOfRows) {
		return parse("gardenList", get("gardenList", Map.of("pageNo", pageNo, "numOfRows", numOfRows)));
	}

	@Override
	public Map<String, String> detail(String cntntsNo) {
		List<Map<String, String>> items = parse("gardenDtl", get("gardenDtl", Map.of("cntntsNo", cntntsNo))).items();
		return items.isEmpty() ? Map.of() : items.get(0);
	}

	/** 종류별 첫 응답은 data/nongsaro-sample/{종류}.xml 로 남기고, 읽지 못한 응답은 {종류}-error.xml 로 남긴다. */
	private NongsaroXml.Page parse(String operation, byte[] body) {
		if (saved.add(operation)) {
			save(operation + ".xml", body);
		}
		try {
			return NongsaroXml.parse(body);
		} catch (RuntimeException e) {
			Path file = save(operation + "-error.xml", body);
			throw new IllegalStateException(e.getMessage() + (file != null ? " (받은 응답: " + file.toAbsolutePath() + ")" : ""), e);
		}
	}

	private Path save(String name, byte[] body) {
		try {
			Files.createDirectories(sampleDir);
			Path file = sampleDir.resolve(name);
			Files.write(file, body);
			return file;
		} catch (IOException e) {
			return null;
		}
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
