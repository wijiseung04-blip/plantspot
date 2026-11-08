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

	@GetMapping("/admin/plants")
	public String admin(Model model) {
		model.addAttribute("count", service.count());
		return "admin/plants";
	}
}
