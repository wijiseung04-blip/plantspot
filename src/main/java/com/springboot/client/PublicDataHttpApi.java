package com.springboot.client;

import com.springboot.dto.KasiRiseSet;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털 HTTP 호출. 키는 application-local.properties:
 * api.kasi.service-key, api.kma.service-key (포털의 "일반 인증키(Decoding)" 값).
 */
@Component
public class PublicDataHttpApi implements PublicDataApi {

	private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String kasiUrl;
	private final String kasiKey;
	private final String kmaUrl;
	private final String kmaKey;

	public PublicDataHttpApi(
			@Value("${api.kasi.url:http://apis.data.go.kr/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo}") String kasiUrl,
			@Value("${api.kasi.service-key:}") String kasiKey,
			@Value("${api.kma.url:http://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList}") String kmaUrl,
			@Value("${api.kma.service-key:}") String kmaKey) {
		this.kasiUrl = kasiUrl;
		this.kasiKey = kasiKey;
		this.kmaUrl = kmaUrl;
		this.kmaKey = kmaKey;
	}

	@Override
	public boolean hasKasiKey() {
		return !kasiKey.isBlank();
	}

	@Override
	public boolean hasKmaKey() {
		return !kmaKey.isBlank();
	}

	@Override
	public KasiRiseSet riseSet(LocalDate date, double latitude, double longitude) {
		if (!hasKasiKey()) {
			throw new IllegalStateException("천문연 API 키(api.kasi.service-key)가 없습니다.");
		}
		String url = kasiUrl + "?ServiceKey=" + enc(kasiKey) + "&locdate=" + date.format(YMD)
				+ "&latitude=" + String.format(Locale.ROOT, "%.4f", latitude)
				+ "&longitude=" + String.format(Locale.ROOT, "%.4f", longitude) + "&dnYn=Y";
		return PublicDataParser.parseRiseSet(get(url));
	}

	@Override
	public PublicDataParser.AsosPage asosDay(LocalDate date, int stationId) {
		if (!hasKmaKey()) {
			throw new IllegalStateException("기상청 API 키(api.kma.service-key)가 없습니다.");
		}
		String d = date.format(YMD);
		String url = kmaUrl + "?serviceKey=" + enc(kmaKey) + "&pageNo=1&numOfRows=24&dataType=JSON&dataCd=ASOS&dateCd=HR"
				+ "&startDt=" + d + "&startHh=00&endDt=" + d + "&endHh=23&stnIds=" + stationId;
		return PublicDataParser.parseAsos(get(url));
	}

	private byte[] get(String url) {
		try {
			HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
					.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200) {
				throw new IllegalStateException("공공데이터 HTTP " + res.statusCode());
			}
			return res.body();
		} catch (IOException e) {
			throw new IllegalStateException("공공데이터 연결 실패: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨", e);
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
