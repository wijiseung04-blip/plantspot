package com.springboot.service;

import com.springboot.domain.FileCategory;
import com.springboot.domain.UploadFile;
import com.springboot.repository.UploadFileRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.springboot.exception.ForbiddenException;
import com.springboot.exception.NotFoundException;
import com.springboot.security.LoginMember;

/** 파일 업로드·다운로드. [필수: 파일 업로드·다운로드] */
@Service
@Transactional(readOnly = true)
public class FileService {

	private static final Logger log = LoggerFactory.getLogger(FileService.class);

	private final UploadFileRepository repository;
	private final FileStorage storage;
	private final MemberService memberService;
	private final HouseService houseService;

	public FileService(UploadFileRepository repository, FileStorage storage, MemberService memberService,
			HouseService houseService) {
		this.repository = repository;
		this.storage = storage;
		this.memberService = memberService;
		this.houseService = houseService;
	}

	@Transactional
	public FileResponse upload(LoginMember me, MultipartFile file, FileCategory category, Long roomId) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("빈 파일입니다.");
		}
		String originalName = cleanName(file.getOriginalFilename());
		String ext = extension(originalName);
		if (!category.getExtensions().contains(ext)) {
			throw new IllegalArgumentException(
					category.getLabel() + "는 " + String.join(", ", category.getExtensions().stream().sorted().toList())
							+ " 파일만 올릴 수 있습니다.");
		}
		if (roomId != null) {
			houseService.loadRoom(me, roomId); // 본인 방인지 확인
		}
		String storedName = UUID.randomUUID() + "." + ext;
		try (InputStream in = file.getInputStream()) {
			storage.save(storedName, in);
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 읽지 못했습니다.", e);
		}
		UploadFile saved = repository.save(new UploadFile(memberService.get(me.getId()), category, originalName,
				storedName, contentType(ext, file.getContentType()), file.getSize(), roomId));
		if (category == FileCategory.FLOOR_PLAN && roomId != null) {
			houseService.attachFloorPlan(me, roomId, saved.getId());
		}
		log.info("파일 업로드: {} {} {}바이트 (회원 {})", category, originalName, file.getSize(), me.getUsername());
		return FileResponse.of(saved);
	}

	public List<FileResponse> myFiles(LoginMember me, FileCategory category) {
		List<UploadFile> files = category == null
				? repository.findByOwnerIdOrderByIdDesc(me.getId())
				: repository.findByOwnerIdAndCategoryOrderByIdDesc(me.getId(), category);
		return files.stream().map(FileResponse::of).toList();
	}

	public FileResponse info(LoginMember me, Long id) {
		return FileResponse.of(load(me, id));
	}

	/** 다운로드용: 기록 + 파일 내용. */
	public Download download(LoginMember me, Long id) {
		UploadFile f = load(me, id);
		return new Download(f.getOriginalName(), f.getContentType(), f.getSize(), storage.load(f.getStoredName()));
	}

	/** 센서 기록 분석 등 다른 서비스가 파일 내용을 읽을 때. */
	public Resource content(LoginMember me, Long id) {
		return storage.load(load(me, id).getStoredName());
	}

	@Transactional
	public void delete(LoginMember me, Long id) {
		UploadFile f = load(me, id);
		repository.delete(f);
		storage.delete(f.getStoredName());
		log.info("파일 삭제: {} (회원 {})", f.getOriginalName(), me.getUsername());
	}

	private UploadFile load(LoginMember me, Long id) {
		UploadFile f = repository.findById(id).orElseThrow(() -> new NotFoundException("파일을 찾을 수 없습니다."));
		if (!f.isOwnedBy(me.getId()) && !me.isAdmin()) {
			throw new ForbiddenException("다른 회원의 파일입니다.");
		}
		return f;
	}

	/** 경로 부분을 떼고 이름만 남긴다 (윈도 브라우저는 전체 경로를 보내기도 함). */
	static String cleanName(String name) {
		if (name == null || name.isBlank()) {
			return "file";
		}
		String n = name.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
		return n.isEmpty() ? "file" : (n.length() > 200 ? n.substring(n.length() - 200) : n);
	}

	static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** 브라우저가 보낸 형식 대신 확장자로 정한다(엉뚱한 형식으로 내려주지 않도록). */
	static String contentType(String ext, String fallback) {
		return switch (ext) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "webp" -> "image/webp";
			case "gif" -> "image/gif";
			case "heic" -> "image/heic";
			case "csv" -> "text/csv";
			case "txt", "log" -> "text/plain";
			default -> fallback != null ? fallback : "application/octet-stream";
		};
	}

	public record Download(String filename, String contentType, long size, Resource resource) {
	}

	public record FileResponse(Long id, FileCategory category, String categoryLabel, String originalName,
			String contentType, long size, Long roomId, LocalDateTime createdAt, String downloadUrl) {

		static FileResponse of(UploadFile f) {
			return new FileResponse(f.getId(), f.getCategory(), f.getCategory().getLabel(), f.getOriginalName(),
					f.getContentType(), f.getSize(), f.getRoomId(), f.getCreatedAt(),
					"/api/files/" + f.getId() + "/content");
		}
	}
}
