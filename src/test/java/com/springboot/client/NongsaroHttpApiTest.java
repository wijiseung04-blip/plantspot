package com.springboot.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/** 농사로 응답 원문 저장(형식 확인·오류 진단) 확인 */
class NongsaroHttpApiTest {

	@TempDir
	Path dir;

	HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private NongsaroHttpApi start(String body) throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			byte[] b = body.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, b.length);
			ex.getResponseBody().write(b);
			ex.close();
		});
		server.start();
		return new NongsaroHttpApi("http://127.0.0.1:" + server.getAddress().getPort() + "/garden", "test-key",
				dir.toString());
	}

	@Test
	void 첫_목록_응답을_파일로_남긴다() throws Exception {
		String xml = "<response><header><resultCode>00</resultCode></header><body><items>"
				+ "<item><cntntsNo>1</cntntsNo><cntntsSj>몬스테라</cntntsSj></item>"
				+ "<totalCount>1</totalCount></items></body></response>";
		NongsaroHttpApi api = start(xml);

		api.list(1, 10);

		Path saved = dir.resolve("gardenList.xml");
		assertThat(saved).exists();
		assertThat(Files.readString(saved)).contains("몬스테라").doesNotContain("test-key");
	}

	@Test
	void 읽지_못한_응답은_오류_파일로_남기고_경로를_알려준다() throws Exception {
		NongsaroHttpApi api = start("<html>점검 중</html");

		assertThatThrownBy(() -> api.list(1, 10)).hasMessageContaining("gardenList-error.xml");
		assertThat(dir.resolve("gardenList-error.xml")).exists();
	}
}
