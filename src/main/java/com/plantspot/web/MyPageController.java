package com.plantspot.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.plantspot.house.CurtainType;

/** 회원 화면. 데이터는 화면의 스크립트가 REST API로 읽고 쓴다(본인 확인은 API에서). */
@Controller
public class MyPageController {

	@GetMapping("/my/houses")
	public String houses() {
		return "my/houses";
	}

	@GetMapping("/my/houses/{id}")
	public String house(@PathVariable Long id, Model model) {
		model.addAttribute("houseId", id);
		model.addAttribute("curtains", CurtainType.values());
		return "my/house";
	}

	@GetMapping("/my/files")
	public String files(Model model) {
		model.addAttribute("categories", com.plantspot.file.FileCategory.values());
		return "my/files";
	}

	@GetMapping("/my/rooms/{id}")
	public String room(@PathVariable Long id, Model model) {
		model.addAttribute("roomId", id);
		model.addAttribute("curtains", CurtainType.values());
		return "my/room";
	}
}
