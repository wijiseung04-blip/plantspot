package com.plantspot.member;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 로그인한 회원. 컨트롤러에서 @AuthenticationPrincipal LoginMember 로 받는다. */
public class LoginMember implements UserDetails {

	private final Long id;
	private final String loginId;
	private final String password;
	private final String nickname;
	private final Role role;

	public LoginMember(Member member) {
		this.id = member.getId();
		this.loginId = member.getLoginId();
		this.password = member.getPassword();
		this.nickname = member.getNickname();
		this.role = member.getRole();
	}

	public Long getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	public Role getRole() {
		return role;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.authority()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return loginId;
	}
}
