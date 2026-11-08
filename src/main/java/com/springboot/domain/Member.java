package com.springboot.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "integer") // SQLite 자동 번호(rowid)가 되려면 타입이 정확히 integer여야 함
	private Long id;

	@Column(name = "login_id", nullable = false, unique = true, length = 20)
	private String loginId;

	/** BCrypt 해시 */
	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Role role;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected Member() {
	}

	public Member(String loginId, String encodedPassword, String nickname, Role role) {
		this.loginId = loginId;
		this.password = encodedPassword;
		this.nickname = nickname;
		this.role = role;
		this.createdAt = LocalDateTime.now();
	}

	public void changeRole(Role role) {
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getPassword() {
		return password;
	}

	public String getNickname() {
		return nickname;
	}

	public Role getRole() {
		return role;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
