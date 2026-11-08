package com.springboot.client;

import com.springboot.domain.WeatherHour;
import com.springboot.dto.KasiRiseSet;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 공공데이터포털 응답 읽기: 천문연 출몰시각(XML), 기상청 ASOS 시간자료(JSON). */
public final class PublicDataParser {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final DateTimeFormatter ASOS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private PublicDataParser() {
	}

	/** 출몰시각 XML → KasiRiseSet. resultCode가 00이 아니거나 항목이 없으면 예외. */
	public static KasiRiseSet parseRiseSet(byte[] xml) {
		Document doc = xml(xml);
		Element root = doc.getDocumentElement();
		String code = text(root, "resultCode");
		if (code != null && !"00".equals(code)) {
			throw new IllegalStateException("천문연 API 오류 " + code + ": " + text(root, "resultMsg"));
		}
		if (doc.getElementsByTagName("item").getLength() == 0) {
			throw new IllegalStateException("천문연 API 응답에 항목이 없습니다.");
		}
		Element item = (Element) doc.getElementsByTagName("item").item(0);
		return new KasiRiseSet(time(text(item, "sunrise")), time(text(item, "suntransit")), time(text(item, "sunset")),
				time(text(item, "civilm")), time(text(item, "civile")), time(text(item, "nautm")),
				time(text(item, "naute")), time(text(item, "astm")), time(text(item, "aste")));
	}

	/** "0652" / "065230" / "06:52" → 06:52. "----", 빈 값 → null */
	static LocalTime time(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.replaceAll("[^0-9]", "");
		if (digits.length() < 4) {
			return null;
		}
		int h = Integer.parseInt(digits.substring(0, 2));
		int m = Integer.parseInt(digits.substring(2, 4));
		return h < 24 && m < 60 ? LocalTime.of(h, m) : null;
	}

	/** ASOS 한 페이지 결과. */
	public record AsosPage(List<WeatherHour> hours, int totalCount) {
	}

	public static AsosPage parseAsos(byte[] json) {
		JsonNode root = JSON.readTree(json);
		JsonNode header = root.path("response").path("header");
		String code = header.path("resultCode").asString();
		if (!"00".equals(code)) {
			// 자료가 없을 때 03(NODATA_ERROR)
			if ("03".equals(code)) {
				return new AsosPage(List.of(), 0);
			}
			throw new IllegalStateException("기상청 API 오류 " + code + ": " + header.path("resultMsg").asString());
		}
		JsonNode body = root.path("response").path("body");
		JsonNode items = body.path("items").path("item");
		List<WeatherHour> hours = new ArrayList<>();
		if (items.isArray()) {
			for (JsonNode n : items) {
				hours.add(hour(n));
			}
		} else if (items.isObject()) {
			hours.add(hour(items));
		}
		return new AsosPage(hours, body.path("totalCount").asInt(hours.size()));
	}

	private static WeatherHour hour(JsonNode n) {
		String qc = n.path("ssQcflg").isMissingNode() ? n.path("ssQcflag").asString(null) : n.path("ssQcflg").asString(null);
		return new WeatherHour(n.path("stnId").asInt(), blank(n.path("stnNm").asString(null)),
				LocalDateTime.parse(n.path("tm").asString(), ASOS_TIME), number(n, "ta"), number(n, "hm"),
				number(n, "ss"), number(n, "icsr"), number(n, "dc10Tca"), blank(qc));
	}

	private static Double number(JsonNode n, String field) {
		JsonNode v = n.path(field);
		if (v.isMissingNode() || v.isNull()) {
			return null;
		}
		String s = v.asString().trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static Document xml(byte[] bytes) {
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			f.setExpandEntityReferences(false);
			return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
		} catch (Exception e) {
			throw new IllegalStateException("응답을 읽을 수 없습니다: " + e.getMessage(), e);
		}
	}

	private static String text(Element root, String tag) {
		NodeList list = root.getElementsByTagName(tag);
		return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
	}
}
