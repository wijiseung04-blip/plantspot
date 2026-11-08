package com.plantspot.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.plantspot.member.MemberService;
import com.plantspot.member.Role;

/** 관리자 화면: 회원 관리. (/admin/** 는 SecurityConfig에서 ADMIN만 허용) */
@Controller
@RequestMapping("/admin")
public class AdminController {

	private final MemberService memberService;

	public AdminController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping({ "", "/members" })
	public String members(Model model) {
		model.addAttribute("members", memberService.findAll());
		model.addAttribute("roles", Role.values());
		return "admin/members";
	}

	@GetMapping("/public-data")
	public String publicData() {
		return "admin/public-data";
	}

	@PostMapping("/members/{id}/role")
	public String changeRole(@PathVariable Long id, @RequestParam Role role, RedirectAttributes redirect) {
		try {
			memberService.changeRole(id, role);
			redirect.addFlashAttribute("message", "권한을 변경했습니다.");
		} catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/members";
	}
}
