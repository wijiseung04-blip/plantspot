package com.plantspot.member;

/** 회원 권한. 비회원은 로그인하지 않은 상태(익명)로 구분한다. [필수: 회원 / 비회원 / 관리자] */
public enum Role {
	USER("회원"),
	ADMIN("관리자");

	private final String label;

	Role(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** Spring Security 권한 이름 (ROLE_USER, ROLE_ADMIN). */
	public String authority() {
		return "ROLE_" + name();
	}
}
