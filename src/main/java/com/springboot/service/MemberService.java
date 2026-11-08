package com.springboot.service;

import com.springboot.domain.Member;
import com.springboot.domain.Role;
import com.springboot.dto.SignupForm;
import com.springboot.repository.MemberRepository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.exception.ConflictException;
import com.springboot.exception.NotFoundException;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private static final Logger log = LoggerFactory.getLogger(MemberService.class);

	private final MemberRepository repository;
	private final PasswordEncoder passwordEncoder;

	public MemberService(MemberRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public Member signup(SignupForm form) {
		return create(form.getLoginId(), form.getPassword(), form.getNickname().trim(), Role.USER);
	}

	@Transactional
	public Member create(String loginId, String rawPassword, String nickname, Role role) {
		if (repository.existsByLoginId(loginId)) {
			throw new ConflictException("이미 사용 중인 아이디입니다.");
		}
		Member member = repository.save(new Member(loginId, passwordEncoder.encode(rawPassword), nickname, role));
		log.info("회원 가입: {} ({})", loginId, role);
		return member;
	}

	public Member get(Long id) {
		return repository.findById(id).orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
	}

	public Member getByLoginId(String loginId) {
		return repository.findByLoginId(loginId).orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
	}

	public List<Member> findAll() {
		return repository.findAllByOrderByIdAsc();
	}

	/** 관리자가 권한 변경. 마지막 관리자는 회원으로 내릴 수 없다. */
	@Transactional
	public void changeRole(Long memberId, Role role) {
		Member member = get(memberId);
		if (member.getRole() == Role.ADMIN && role != Role.ADMIN && repository.countByRole(Role.ADMIN) <= 1) {
			throw new IllegalArgumentException("마지막 관리자는 권한을 내릴 수 없습니다.");
		}
		member.changeRole(role);
		log.info("권한 변경: {} → {}", member.getLoginId(), role);
	}
}
