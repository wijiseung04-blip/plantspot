package com.plantspot.backend.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

class NongsaroXmlTest {

	static byte[] resource(String name) {
		try (InputStream in = NongsaroXmlTest.class.getResourceAsStream("/nongsaro/" + name)) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void 목록_읽기() {
		NongsaroXml.Page p = NongsaroXml.parse(resource("list-page1.xml"));
		assertThat(p.items()).hasSize(2);
		assertThat(p.totalCount()).isEqualTo(3);
		assertThat(p.items().get(0).get("cntntsSj")).isEqualTo("테스트덩굴");
	}

	@Test
	void 사진_주소는_첫번째_파일을_쓰고_URL이_있으면_우선() {
		NongsaroXml.Page p = NongsaroXml.parse(resource("list-page1.xml"));
		assertThat(NongsaroXml.thumbnail(p.items().get(0)))
				.isEqualTo("http://www.nongsaro.go.kr/cms_contents/301/t1_s.jpg");
		assertThat(NongsaroXml.image(p.items().get(0))).isEqualTo("http://www.nongsaro.go.kr/cms_contents/301/t1.jpg");
		assertThat(NongsaroXml.thumbnail(p.items().get(1))).isEqualTo("http://example.org/thumb.jpg");
	}

	@Test
	void 목록과_상세를_합친다() {
		var item = NongsaroXml.parse(resource("list-page1.xml")).items().get(0);
		var detail = NongsaroXml.parse(resource("detail-90001.xml")).items().get(0);
		NongsaroPlant p = NongsaroXml.toPlant(item, detail);
		assertThat(p.cntntsNo()).isEqualTo("90001");
		assertThat(p.name()).isEqualTo("테스트덩굴");
		assertThat(p.lightCodes()).isEqualTo("055001,055002");
		assertThat(p.winterMinCode()).isEqualTo("057003");
		assertThat(p.manageLevelName()).isEqualTo("초보자");
	}

	@Test
	void 결과_코드가_00이_아니면_오류() {
		assertThatThrownBy(() -> NongsaroXml.parse(resource("error.xml")))
				.hasMessageContaining("11").hasMessageContaining("등록되지 않은 키");
	}

	@Test
	void DOCTYPE_외부_엔티티는_막는다() {
		byte[] xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><response>&x;</response>"
				.getBytes();
		assertThatThrownBy(() -> NongsaroXml.parse(xxe)).isInstanceOf(IllegalStateException.class);
	}
}
