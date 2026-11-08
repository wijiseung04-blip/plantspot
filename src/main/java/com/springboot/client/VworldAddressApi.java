package com.springboot.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 브이월드 검색 API(주소). 좌표는 건물 데이터와 같은 EPSG:5186 으로 받는다.
 * 키: api.vworld.key (브이월드에서 발급), 키에 등록한 서비스 URL이 필요하면 api.vworld.domain.
 */
@Component
public class VworldAddressApi implements AddressApi {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final String url;
	private final String key;
	private final String domain;

	public VworldAddressApi(@Value("${api.vworld.url:https://api.vworld.kr/req/search}") String url,
			@Value("${api.vworld.key:}") String key, @Value("${api.vworld.domain:}") String domain) {
		this.url = url;
		this.key = key;
		this.domain = domain;
	}

	@Override
	public boolean hasKey() {
		return !key.isBlank();
	}

	@Override
	public List<Candidate> search(String query, String category) {
		if (!hasKey()) {
			throw new IllegalStateException("브이월드 API 키(api.vworld.key)가 없습니다.");
		}
		String u = url + "?service=search&request=search&version=2.0&crs=EPSG:5186&size=10&page=1&type=address"
				+ "&format=json&errorformat=json&category=" + category + "&query=" + enc(query) + "&key=" + enc(key)
				+ (domain.isBlank() ? "" : "&domain=" + enc(domain));
		try {
			HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10))
					.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200) {
				throw new IllegalStateException("브이월드 HTTP " + res.statusCode());
			}
			return parse(res.body());
		} catch (IOException e) {
			throw new IllegalStateException("브이월드 연결 실패: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨", e);
		}
	}

	/** 브이월드 검색 응답(JSON) → 후보 목록. NOT_FOUND 면 빈 목록. */
	static List<Candidate> parse(byte[] json) {
		JsonNode response = JSON.readTree(json).path("response");
		String status = response.path("status").asString("");
		if ("NOT_FOUND".equals(status)) {
			return List.of();
		}
		if (!"OK".equals(status)) {
			throw new IllegalStateException("브이월드 오류: " + response.path("error").path("text").asString(status));
		}
		List<Candidate> list = new ArrayList<>();
		for (JsonNode item : response.path("result").path("items")) {
			JsonNode a = item.path("address");
			JsonNode p = item.path("point");
			list.add(new Candidate(blank(a.path("road").asString(null)), blank(a.path("parcel").asString(null)),
					blank(a.path("zipcode").asString(null)), blank(a.path("bldnm").asString(null)),
					blank(a.path("bldnmdc").asString(null)),
					Double.parseDouble(p.path("x").asString("0")), Double.parseDouble(p.path("y").asString("0"))));
		}
		return list;
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
