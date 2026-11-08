package com.plantspot.web;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.common.ErrorResponse;
import com.plantspot.member.LoginMember;
import com.plantspot.member.Member;
import com.plantspot.member.MemberService;
import com.plantspot.member.SignupForm;
import com.plantspot.web.MemberApiController.MeResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** 로그인·회원가입 REST API. [필수: 로그인·회원가입] */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository contextRepository;
	private final MemberService memberService;

	public AuthApiController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
			MemberService memberService) {
		this.authenticationManager = authenticationManager;
		this.contextRepository = contextRepository;
		this.memberService = memberService;
	}

	/** CSRF 토큰 발급 (XSRF-TOKEN 쿠키도 함께 내려간다). 화면은 POST 전에 한 번 부른다. */
	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

	public record LoginRequest(@NotBlank(message = "아이디를 입력하세요.") String loginId,
			@NotBlank(message = "비밀번호를 입력하세요.") String password) {
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication auth;
		try {
			auth = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(req.loginId(), req.password()));
		} catch (AuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(401, "Unauthorized",
					"아이디 또는 비밀번호가 올바르지 않습니다.", request.getRequestURI()));
		}
		request.getSession(true);
		request.changeSessionId(); // 세션 고정 공격 방지
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(auth);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		LoginMember me = (LoginMember) auth.getPrincipal();
		return ResponseEntity.ok(new MeResponse(me.getId(), me.getUsername(), me.getNickname(), me.getRole()));
	}

	@PostMapping("/signup")
	public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupForm form) {
		Member m = memberService.signup(form);
		return ResponseEntity.created(URI.create("/api/members/" + m.getId()))
				.body(new MeResponse(m.getId(), m.getLoginId(), m.getNickname(), m.getRole()));
	}
}
