package com.plantspot.web;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.member.MemberService;
import com.plantspot.member.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 관리자: 회원 관리. (/api/admin/** 는 ADMIN만) */
@RestController
@RequestMapping("/api/admin/members")
public class AdminApiController {

	private final MemberService memberService;

	public AdminApiController(MemberService memberService) {
		this.memberService = memberService;
	}

	public record MemberRow(Long id, String loginId, String nickname, Role role, String roleLabel,
			LocalDateTime createdAt) {
	}

	public record RoleRequest(@NotNull(message = "권한을 고르세요.") Role role) {
	}

	@GetMapping
	public List<MemberRow> members() {
		return memberService.findAll().stream()
				.map(m -> new MemberRow(m.getId(), m.getLoginId(), m.getNickname(), m.getRole(), m.getRole().getLabel(),
						m.getCreatedAt()))
				.toList();
	}

	@PutMapping("/{id}/role")
	public MemberRow changeRole(@PathVariable("id") Long id, @Valid @RequestBody RoleRequest req) {
		memberService.changeRole(id, req.role());
		var m = memberService.get(id);
		return new MemberRow(m.getId(), m.getLoginId(), m.getNickname(), m.getRole(), m.getRole().getLabel(),
				m.getCreatedAt());
	}
}
