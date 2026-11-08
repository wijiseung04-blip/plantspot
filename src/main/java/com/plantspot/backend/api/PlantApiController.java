package com.plantspot.backend.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.plant.PlantImportJob;
import com.plantspot.backend.plant.PlantService;
import com.plantspot.backend.plant.PlantService.ConversionRequest;
import com.plantspot.backend.plant.PlantService.PlantPage;
import com.plantspot.backend.plant.PlantService.PlantResponse;

import jakarta.validation.Valid;

/** 식물 검색(비회원 가능)과 관리자 식물 관리. */
@RestController
public class PlantApiController {

	private final PlantService service;
	private final PlantImportJob importJob;

	public PlantApiController(PlantService service, PlantImportJob importJob) {
		this.service = service;
		this.importJob = importJob;
	}

	/** GET /api/plants?keyword=스킨&light=055001&level=089001&page=0&size=20 */
	@GetMapping("/api/plants")
	public PlantPage search(@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "light", required = false) String light, @RequestParam(name = "level", required = false) String level,
			@RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
		return service.search(keyword, light, level, page, size);
	}

	@GetMapping("/api/plants/{id}")
	public PlantResponse get(@PathVariable("id") Long id) {
		return service.get(id);
	}

	// ----- 관리자 -----

	@PostMapping("/api/admin/plants")
	public ResponseEntity<PlantResponse> create(@Valid @RequestBody ConversionRequest req) {
		PlantResponse p = service.createManual(req);
		return ResponseEntity.created(URI.create("/api/plants/" + p.id())).body(p);
	}

	@PutMapping("/api/admin/plants/{id}/conversion")
	public PlantResponse adjust(@PathVariable("id") Long id, @Valid @RequestBody ConversionRequest req) {
		return service.adjust(id, req);
	}

	@PostMapping("/api/admin/plants/{id}/conversion/reset")
	public PlantResponse reset(@PathVariable("id") Long id) {
		return service.reset(id);
	}

	@DeleteMapping("/api/admin/plants/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** 농사로 가져오기 시작 (뒤에서 실행). */
	@PostMapping("/api/admin/plants/import")
	public ResponseEntity<PlantImportJob.Status> startImport() {
		boolean started = importJob.start();
		return ResponseEntity.status(started ? 202 : 409).body(importJob.status());
	}

	@GetMapping("/api/admin/plants/import")
	public PlantImportJob.Status importStatus() {
		return importJob.status();
	}
}
