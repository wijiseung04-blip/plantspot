package com.plantspot.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.member.LoginMember;
import com.plantspot.obstruction.ObstructionService;
import com.plantspot.obstruction.ObstructionService.MeasuredAngle;
import com.plantspot.obstruction.ObstructionService.ObstructionView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** 집에서 본 방향별 가림 각도 (72칸, 5° 간격). */
@RestController
@RequestMapping("/api/houses/{houseId}/obstructions")
public class ObstructionApiController {

	private final ObstructionService service;

	public ObstructionApiController(ObstructionService service) {
		this.service = service;
	}

	@GetMapping
	public List<ObstructionView> get(@AuthenticationPrincipal LoginMember me, @PathVariable("houseId") Long houseId) {
		return service.view(me, houseId);
	}

	/** 건물 데이터로 다시 계산 (층을 바꾸었거나 건물 데이터를 갱신했을 때). */
	@PostMapping("/recalculate")
	public List<ObstructionView> recalculate(@AuthenticationPrincipal LoginMember me, @PathVariable("houseId") Long houseId) {
		return service.recalculate(me, houseId);
	}

	/** 실측 가림 각도 저장 (센서 분석 결과, 직접 측정). */
	@PutMapping("/measured")
	public List<ObstructionView> saveMeasured(@AuthenticationPrincipal LoginMember me, @PathVariable("houseId") Long houseId,
			@RequestBody @NotEmpty(message = "실측 값이 없습니다.") List<@Valid MeasuredAngle> measured) {
		return service.saveMeasured(me, houseId, measured);
	}

	@DeleteMapping("/measured")
	public List<ObstructionView> clearMeasured(@AuthenticationPrincipal LoginMember me, @PathVariable("houseId") Long houseId) {
		return service.clearMeasured(me, houseId);
	}
}
