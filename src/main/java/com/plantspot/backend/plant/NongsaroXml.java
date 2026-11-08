package com.plantspot.backend.plant;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** 농사로 XML 응답 읽기. 외부 엔티티(XXE)는 막는다. */
public final class NongsaroXml {

	public static final String IMAGE_BASE = "http://www.nongsaro.go.kr/";

	private NongsaroXml() {
	}

	public record Page(List<Map<String, String>> items, int pageNo, int numOfRows, int totalCount) {
	}

	public static Page parse(byte[] xml) {
		Document doc = document(xml);
		String code = text(doc.getDocumentElement(), "resultCode");
		if (code != null && !"00".equals(code)) {
			throw new IllegalStateException("농사로 오류 " + code + ": " + text(doc.getDocumentElement(), "resultMsg"));
		}
		List<Map<String, String>> items = new ArrayList<>();
		NodeList nodes = doc.getElementsByTagName("item");
		for (int i = 0; i < nodes.getLength(); i++) {
			Map<String, String> map = new LinkedHashMap<>();
			NodeList children = nodes.item(i).getChildNodes();
			for (int j = 0; j < children.getLength(); j++) {
				Node c = children.item(j);
				if (c.getNodeType() == Node.ELEMENT_NODE) {
					String v = c.getTextContent();
					map.put(c.getNodeName(), v == null ? null : v.trim());
				}
			}
			items.add(map);
		}
		return new Page(items, intOf(text(doc.getDocumentElement(), "pageNo")),
				intOf(text(doc.getDocumentElement(), "numOfRows")), intOf(text(doc.getDocumentElement(), "totalCount")));
	}

	/** 목록 항목 + 상세 항목 → NongsaroPlant */
	public static NongsaroPlant toPlant(Map<String, String> listItem, Map<String, String> detail) {
		Map<String, String> m = new LinkedHashMap<>(listItem);
		detail.forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				m.put(k, v);
			}
		});
		return new NongsaroPlant(m.get("cntntsNo"), blankToNull(m.getOrDefault("cntntsSj", m.get("distbNm"))),
				blankToNull(m.get("plntbneNm")), blankToNull(m.get("plntzrNm")),
				blankToNull(m.getOrDefault("fmlCodeNm", m.get("fmlNm"))), thumbnail(m), image(m),
				blankToNull(m.get("lighttdemanddoCode")), blankToNull(m.get("lighttdemanddoCodeNm")),
				blankToNull(m.get("postngplaceCode")), blankToNull(m.get("postngplaceCodeNm")),
				blankToNull(m.get("winterLwetTpCode")), blankToNull(m.get("winterLwetTpCodeNm")),
				blankToNull(m.get("grwhTpCodeNm")), blankToNull(m.get("hdCodeNm")),
				blankToNull(m.get("managelevelCode")), blankToNull(m.get("managelevelCodeNm")),
				blankToNull(m.get("watercycleSprngCodeNm")), blankToNull(m.get("watercycleSummerCodeNm")),
				blankToNull(m.get("watercycleAutumnCodeNm")), blankToNull(m.get("watercycleWinterCodeNm")),
				blankToNull(m.get("adviseInfo")), blankToNull(m.get("growthHgInfo")));
	}

	/** rtnThumbFileUrl 이 있으면 그 첫 번째, 없으면 기본 주소 + rtnFileCours + / + rtnThumbFileNm 첫 번째. */
	static String thumbnail(Map<String, String> m) {
		String url = first(m.get("rtnThumbFileUrl"));
		if (url != null) {
			return url;
		}
		String course = first(m.get("rtnFileCours"));
		String name = first(m.get("rtnThumbFileNm"));
		return course != null && name != null ? IMAGE_BASE + course + "/" + name : null;
	}

	static String image(Map<String, String> m) {
		String url = first(m.get("rtnFileUrl"));
		if (url != null) {
			return url;
		}
		String course = first(m.get("rtnFileCours"));
		String name = first(m.get("rtnStreFileNm"));
		return course != null && name != null ? IMAGE_BASE + course + "/" + name : thumbnail(m);
	}

	private static String first(String pipeSeparated) {
		if (pipeSeparated == null || pipeSeparated.isBlank()) {
			return null;
		}
		String f = pipeSeparated.split("\\|")[0].trim();
		return f.isEmpty() ? null : f;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static Document document(byte[] xml) {
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			f.setExpandEntityReferences(false);
			return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
		} catch (Exception e) {
			throw new IllegalStateException("농사로 응답을 읽을 수 없습니다: " + e.getMessage(), e);
		}
	}

	private static String text(Element root, String tag) {
		NodeList list = root.getElementsByTagName(tag);
		return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
	}

	private static int intOf(String s) {
		try {
			return s == null ? 0 : Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
