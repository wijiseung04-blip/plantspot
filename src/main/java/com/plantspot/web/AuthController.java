package com.plantspot.web;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.plantspot.common.ConflictException;
import com.plantspot.member.MemberService;
import com.plantspot.member.SignupForm;

import jakarta.validation.Valid;

/** 로그인·회원가입 화면. 로그인 처리(POST /login)는 Spring Security가 한다. */
@Controller
public class AuthController {

	private final MemberService memberService;

	public AuthController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping("/login")
	public String loginForm() {
		return "login";
	}

	@GetMapping("/signup")
	public String signupForm(@ModelAttribute("form") SignupForm form) {
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult binding,
			RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return "signup";
		}
		try {
			memberService.signup(form);
		} catch (ConflictException e) {
			binding.rejectValue("loginId", "duplicate", e.getMessage());
			return "signup";
		}
		redirect.addFlashAttribute("message", "가입되었습니다. 로그인하세요.");
		return "redirect:/login";
	}
}
