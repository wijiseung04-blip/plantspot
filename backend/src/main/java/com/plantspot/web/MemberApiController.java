package com.plantspot.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.member.LoginMember;
import com.plantspot.member.Role;

@RestController
@RequestMapping("/api/members")
public class MemberApiController {

	/** 로그인한 회원 정보. 비회원은 401. */
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal LoginMember me) {
		return new MeResponse(me.getId(), me.getUsername(), me.getNickname(), me.getRole());
	}

	public record MeResponse(Long id, String loginId, String nickname, Role role) {
	}
}
