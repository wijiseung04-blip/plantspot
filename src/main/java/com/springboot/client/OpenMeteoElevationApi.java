package com.springboot.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.springboot.calculator.TmConverter.LatLon;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open-Meteo 고도 API (키 없음, Copernicus DEM GLO-90 = 90m 격자).
 * GET {base}?latitude=a,b,…&longitude=c,d,… → {"elevation":[…]}. 비상업 무료, 출처 표시(Copernicus·Open-Meteo) 필요.
 * 너무 자주 부르면 429 → 잠시 기다렸다 다시 시도한다.
 */
@Component
public class OpenMeteoElevationApi implements ElevationApi {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final int MAX_BATCH = 100;
	private static final int RETRIES = 4;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String url;
	private final long retryWaitMs;

	public OpenMeteoElevationApi(@Value("${api.elevation.url:https://api.open-meteo.com/v1/elevation}") String url,
			@Value("${api.elevation.retry-wait-ms:20000}") long retryWaitMs) {
		this.url = url;
		this.retryWaitMs = retryWaitMs;
	}

	@Override
	public int maxBatch() {
		return MAX_BATCH;
	}

	@Override
	public String sourceName() {
		return "Open-Meteo Elevation API (Copernicus DEM GLO-90)";
	}

	@Override
	public double[] elevations(List<LatLon> points) {
		if (points.isEmpty()) {
			return new double[0];
		}
		String u = url + "?latitude=" + join(points, true) + "&longitude=" + join(points, false);
		for (int attempt = 1;; attempt++) {
			try {
				HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(u))
						.timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
				if ((res.statusCode() == 429 || res.statusCode() >= 500) && attempt < RETRIES) {
					Thread.sleep(retryWaitMs * attempt);
					continue;
				}
				if (res.statusCode() != 200) {
					throw new IllegalStateException("고도 API HTTP " + res.statusCode() + " " + reason(res.body()));
				}
				return parse(res.body(), points.size());
			} catch (IOException e) {
				if (attempt < RETRIES) {
					sleep(retryWaitMs);
					continue;
				}
				throw new IllegalStateException("고도 API 연결 실패: " + e.getMessage(), e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("중단됨", e);
			}
		}
	}

	/** {"elevation":[…]} → 배열. 개수가 다르면 오류. */
	static double[] parse(byte[] body, int expected) {
		JsonNode root = JSON.readTree(body);
		if (root.path("error").asBoolean(false)) {
			throw new IllegalStateException("고도 API 오류: " + root.path("reason").asString(""));
		}
		JsonNode arr = root.path("elevation");
		if (!arr.isArray() || arr.size() != expected) {
			throw new IllegalStateException("고도 API 응답 형식이 다릅니다 (elevation " + arr.size() + "개, 기대 " + expected + "개)");
		}
		double[] out = new double[expected];
		for (int i = 0; i < expected; i++) {
			JsonNode v = arr.get(i);
			out[i] = v == null || v.isNull() ? 0 : v.asDouble();
		}
		return out;
	}

	private static String reason(byte[] body) {
		try {
			return JSON.readTree(body).path("reason").asString("");
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static String join(List<LatLon> points, boolean lat) {
		return points.stream().map(p -> String.format(Locale.ROOT, "%.5f", lat ? p.latitude() : p.longitude()))
				.collect(Collectors.joining(","));
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
