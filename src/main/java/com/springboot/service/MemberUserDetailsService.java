package com.springboot.service;

import com.springboot.repository.MemberRepository;
import com.springboot.security.LoginMember;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {

	private final MemberRepository repository;

	public MemberUserDetailsService(MemberRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String loginId) {
		return repository.findByLoginId(loginId)
				.map(LoginMember::new)
				.orElseThrow(() -> new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다."));
	}
}
