package com.springboot.support;

import java.io.IOException;
import java.io.InputStream;

/** 테스트용 샘플 응답 파일 읽기 (src/test/resources 아래). */
public final class TestResources {

	private TestResources() {
	}

	public static byte[] read(String path) {
		try (InputStream in = TestResources.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalArgumentException("테스트 파일이 없습니다: " + path);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
