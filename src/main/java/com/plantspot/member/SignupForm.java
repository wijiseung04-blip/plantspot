package com.plantspot.member;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 입력값. [필수: Bean Validation] */
public class SignupForm {

	@NotBlank(message = "아이디를 입력하세요.")
	@Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·밑줄 4~20자입니다.")
	private String loginId;

	@NotBlank(message = "비밀번호를 입력하세요.")
	@Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
	private String password;

	@NotBlank(message = "비밀번호 확인을 입력하세요.")
	private String passwordConfirm;

	@NotBlank(message = "닉네임을 입력하세요.")
	@Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
	private String nickname;

	@AssertTrue(message = "비밀번호 확인이 일치하지 않습니다.")
	public boolean isPasswordMatching() {
		return password == null || password.equals(passwordConfirm);
	}

	public String getLoginId() {
		return loginId;
	}

	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPasswordConfirm() {
		return passwordConfirm;
	}

	public void setPasswordConfirm(String passwordConfirm) {
		this.passwordConfirm = passwordConfirm;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}
}
