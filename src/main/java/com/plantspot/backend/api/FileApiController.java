package com.plantspot.backend.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.plantspot.backend.file.FileCategory;
import com.plantspot.backend.file.FileService;
import com.plantspot.backend.file.FileService.Download;
import com.plantspot.backend.file.FileService.FileResponse;
import com.plantspot.backend.member.LoginMember;

/** 파일 업로드·다운로드 API (회원 전용). [필수: 파일 업로드·다운로드] */
@RestController
@RequestMapping("/api/files")
public class FileApiController {

	private final FileService service;

	public FileApiController(FileService service) {
		this.service = service;
	}

	/** multipart/form-data: file, category(SENSOR_LOG / FLOOR_PLAN / PLANT_PHOTO), roomId(선택) */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FileResponse> upload(@AuthenticationPrincipal LoginMember me,
			@RequestPart("file") MultipartFile file, @RequestParam("category") FileCategory category,
			@RequestParam(name = "roomId", required = false) Long roomId) {
		FileResponse saved = service.upload(me, file, category, roomId);
		return ResponseEntity.created(URI.create("/api/files/" + saved.id())).body(saved);
	}

	@GetMapping
	public List<FileResponse> myFiles(@AuthenticationPrincipal LoginMember me,
			@RequestParam(name = "category", required = false) FileCategory category) {
		return service.myFiles(me, category);
	}

	@GetMapping("/{id}")
	public FileResponse info(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.info(me, id);
	}

	/** 다운로드. inline=true 이면 브라우저에서 바로 보기(이미지 미리보기). */
	@GetMapping("/{id}/content")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@RequestParam(name = "inline", defaultValue = "false") boolean inline) {
		Download d = service.download(me, id);
		ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(d.filename(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(d.contentType()))
				.contentLength(d.size())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(d.resource());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		service.delete(me, id);
		return ResponseEntity.noContent().build();
	}
}
