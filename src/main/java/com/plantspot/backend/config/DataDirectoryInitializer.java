package com.plantspot.backend.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * SQLite 파일이 들어갈 data/ 폴더가 없으면 DataSource 생성 전에 미리 만든다.
 * (SQLite 드라이버는 파일은 만들어 주지만 폴더는 만들지 않는다.)
 */
public class DataDirectoryInitializer implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String url = environment.getProperty("spring.datasource.url", "");
		if (!url.startsWith("jdbc:sqlite:") || url.contains(":memory:")) {
			return;
		}
		Path parent = Path.of(url.substring("jdbc:sqlite:".length())).toAbsolutePath().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (IOException e) {
			throw new UncheckedIOException("data 폴더를 만들 수 없습니다: " + parent, e);
		}
	}
}
