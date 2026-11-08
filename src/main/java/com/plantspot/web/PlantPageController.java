package com.plantspot.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.plantspot.plant.NongsaroCodes;
import com.plantspot.plant.PlantService;

/** 식물 검색·상세 화면(비회원 가능)과 관리자 식물 변환표 화면. */
@Controller
public class PlantPageController {

	private final PlantService service;

	public PlantPageController(PlantService service) {
		this.service = service;
	}

	@GetMapping("/plants")
	public String search(Model model) {
		model.addAttribute("lights", new java.util.TreeMap<>(NongsaroCodes.LIGHT_LABEL));
		return "plants/search";
	}

	@GetMapping("/plants/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("plant", service.get(id));
		return "plants/detail";
	}

	/** 비회원 간단 예측 */
	@GetMapping("/try")
	public String tryPreview() {
		return "try";
	}

	/** [② 평면도 햇빛 에디터] 비회원도 쓸 수 있는 브라우저 프로그램. ?roomId= 이면 회원의 방을 불러온다. */
	@GetMapping("/editor")
	public String editor() {
		return "editor";
	}

	@GetMapping("/admin/plants")
	public String admin(Model model) {
		model.addAttribute("count", service.count());
		return "admin/plants";
	}
}
