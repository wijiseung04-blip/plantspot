package com.springboot.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 업로드 파일을 디스크(app.upload-dir)에 저장·읽기·삭제한다. */
@Component
public class FileStorage {

	private final Path root;

	public FileStorage(@Value("${app.upload-dir:data/uploads}") String uploadDir) {
		this.root = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public void save(String storedName, InputStream in) {
		try {
			Files.createDirectories(root);
			Files.copy(in, resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 저장하지 못했습니다.", e);
		}
	}

	public Resource load(String storedName) {
		Path path = resolve(storedName);
		if (!Files.isRegularFile(path)) {
			throw new com.springboot.exception.NotFoundException("파일 내용이 없습니다.");
		}
		return new FileSystemResource(path);
	}

	public void delete(String storedName) {
		try {
			Files.deleteIfExists(resolve(storedName));
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 삭제하지 못했습니다.", e);
		}
	}

	/** 저장 폴더 밖으로 나가는 경로(../ 등)는 거절한다. */
	private Path resolve(String storedName) {
		Path path = root.resolve(storedName).normalize();
		if (!path.getParent().equals(root)) {
			throw new IllegalArgumentException("잘못된 파일 이름입니다.");
		}
		return path;
	}
}
