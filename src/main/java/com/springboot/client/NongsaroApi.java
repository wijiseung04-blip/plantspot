package com.springboot.client;

import java.util.Map;

/** 농사로 실내정원용 식물 API (테스트에서는 가짜 구현으로 바꾼다). */
public interface NongsaroApi {

	NongsaroXml.Page list(int pageNo, int numOfRows);

	Map<String, String> detail(String cntntsNo);
}
