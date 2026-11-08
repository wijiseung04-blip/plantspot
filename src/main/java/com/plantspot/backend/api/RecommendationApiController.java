package com.plantspot.backend.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.recommend.MyPlantService;
import com.plantspot.backend.recommend.MyPlantService.Alert;
import com.plantspot.backend.recommend.MyPlantService.Move;
import com.plantspot.backend.recommend.MyPlantService.MyPlantView;
import com.plantspot.backend.recommend.MyPlantService.SaveMyPlant;
import com.plantspot.backend.recommend.PreviewService;
import com.plantspot.backend.recommend.PreviewService.PreviewRequest;
import com.plantspot.backend.recommend.PreviewService.PreviewResult;
import com.plantspot.backend.recommend.RecommendationService;
import com.plantspot.backend.recommend.RecommendationService.PlantFit;
import com.plantspot.backend.recommend.RecommendationService.RecommendationView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 식물 추천, 내 식물, 비회원 간단 예측. */
@RestController
public class RecommendationApiController {

	private final RecommendationService recommendations;
	private final MyPlantService myPlants;
	private final PreviewService preview;

	public RecommendationApiController(RecommendationService recommendations, MyPlantService myPlants,
			PreviewService preview) {
		this.recommendations = recommendations;
		this.myPlants = myPlants;
		this.preview = preview;
	}

	@GetMapping("/api/rooms/{id}/recommendations")
	public RecommendationView recommendations(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return recommendations.recommendations(me, id);
	}

	@PostMapping("/api/rooms/{id}/recommendations/refresh")
	public RecommendationView refresh(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@RequestParam(name = "limit", defaultValue = "10") @Min(value = 1, message = "개수는 1~50입니다.")
			@Max(value = 50, message = "개수는 1~50입니다.") int limit) {
		return recommendations.refresh(me, id, limit);
	}

	/** 한 식물의 계절별 칸 점수(적합도 지도) */
	@GetMapping("/api/rooms/{id}/recommendations/{plantId}")
	public PlantFit fit(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id, @PathVariable("plantId") Long plantId) {
		return recommendations.fit(me, id, plantId);
	}

	// ----- 내 식물 -----

	@PostMapping("/api/my-plants")
	public ResponseEntity<MyPlantView> create(@AuthenticationPrincipal LoginMember me,
			@Valid @RequestBody SaveMyPlant req) {
		MyPlantView v = myPlants.create(me, req);
		return ResponseEntity.created(URI.create("/api/my-plants/" + v.id())).body(v);
	}

	@GetMapping("/api/my-plants")
	public List<MyPlantView> list(@AuthenticationPrincipal LoginMember me) {
		return myPlants.list(me);
	}

	@PutMapping("/api/my-plants/{id}")
	public MyPlantView update(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody SaveMyPlant req) {
		return myPlants.update(me, id, req);
	}

	/** 위치만 옮기기 (에디터에서 끌어다 놓기) */
	@PatchMapping("/api/my-plants/{id}/position")
	public MyPlantView move(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id, @RequestBody Move req) {
		return myPlants.move(me, id, req);
	}

	@DeleteMapping("/api/my-plants/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		myPlants.delete(me, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/my-plants/alerts")
	public List<Alert> alerts(@AuthenticationPrincipal LoginMember me) {
		return myPlants.alerts(me);
	}

	// ----- 비회원 간단 예측 -----

	@PostMapping("/api/preview")
	public PreviewResult preview(@Valid @RequestBody PreviewRequest req) {
		return preview.preview(req);
	}
}
