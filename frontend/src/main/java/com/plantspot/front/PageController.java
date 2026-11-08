package com.plantspot.front;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import com.plantspot.front.BackendClient.Me;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 화면 주소 → Thymeleaf 템플릿. 데이터는 각 화면의 스크립트가 /api/** (→ backend) 로 읽고 쓴다.
 * 권한: 비회원(첫 화면·간단 예측·식물 검색·에디터·로그인), 회원(/my/**), 관리자(/admin/**) — LoginInterceptor
 */
@Controller
public class PageController {

	/** 모든 화면의 머리글에서 쓰는 로그인 회원 (없으면 null) */
	@ModelAttribute("me")
	public Me me(HttpServletRequest req) {
		return (Me) req.getAttribute(LoginInterceptor.ME);
	}

	// ----- 비회원 -----

	@GetMapping("/")
	public String home() {
		return "index";
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/signup")
	public String signup() {
		return "signup";
	}

	@GetMapping("/try")
	public String tryPreview() {
		return "try";
	}

	@GetMapping("/plants")
	public String plants(Model model) {
		model.addAttribute("lights", Codes.LIGHTS);
		return "plants/search";
	}

	@GetMapping("/plants/{id}")
	public String plant(@PathVariable Long id, Model model) {
		model.addAttribute("plantId", id);
		return "plants/detail";
	}

	/** [② 평면도 햇빛 에디터] 비회원도 사용, ?roomId= 이면 회원의 방을 불러온다 */
	@GetMapping("/editor")
	public String editor() {
		return "editor";
	}

	// ----- 회원 -----

	@GetMapping("/my/houses")
	public String houses() {
		return "my/houses";
	}

	@GetMapping("/my/houses/{id}")
	public String house(@PathVariable Long id, Model model) {
		model.addAttribute("houseId", id);
		model.addAttribute("curtains", Codes.CURTAINS);
		return "my/house";
	}

	@GetMapping("/my/rooms/{id}")
	public String room(@PathVariable Long id, Model model) {
		model.addAttribute("roomId", id);
		model.addAttribute("curtains", Codes.CURTAINS);
		return "my/room";
	}

	@GetMapping("/my/rooms/{id}/sunlight")
	public String sunlight(@PathVariable Long id, Model model) {
		model.addAttribute("roomId", id);
		model.addAttribute("seasons", Codes.SEASONS);
		return "my/sunlight";
	}

	@GetMapping("/my/rooms/{id}/recommendations")
	public String recommendations(@PathVariable Long id, Model model) {
		model.addAttribute("roomId", id);
		model.addAttribute("seasons", Codes.SEASONS);
		return "my/recommendations";
	}

	@GetMapping("/my/plants")
	public String myPlants() {
		return "my/plants";
	}

	@GetMapping("/my/files")
	public String files(Model model) {
		model.addAttribute("categories", Codes.FILE_CATEGORIES);
		return "my/files";
	}

	// ----- 관리자 -----

	@GetMapping({ "/admin", "/admin/members" })
	public String adminMembers() {
		return "admin/members";
	}

	@GetMapping("/admin/plants")
	public String adminPlants() {
		return "admin/plants";
	}

	@GetMapping("/admin/public-data")
	public String adminPublicData() {
		return "admin/public-data";
	}
}
