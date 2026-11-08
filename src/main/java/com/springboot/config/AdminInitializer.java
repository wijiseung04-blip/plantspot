package com.springboot.config;

import com.springboot.domain.Role;
import com.springboot.repository.MemberRepository;
import com.springboot.service.MemberService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 관리자가 한 명도 없으면 application-local.properties 의 app.admin.* 값으로 관리자 계정을 만든다.
 * 비밀번호를 설정하지 않으면 만들지 않는다(기본 비밀번호를 코드에 두지 않기 위해).
 */
@Component
public class AdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

	private final MemberRepository repository;
	private final MemberService memberService;
	private final String loginId;
	private final String password;

	public AdminInitializer(MemberRepository repository, MemberService memberService,
			@Value("${app.admin.login-id:admin}") String loginId,
			@Value("${app.admin.password:}") String password) {
		this.repository = repository;
		this.memberService = memberService;
		this.loginId = loginId;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repository.countByRole(Role.ADMIN) > 0) {
			return;
		}
		if (password.isBlank()) {
			log.warn("관리자 계정이 없습니다. application-local.properties 에 app.admin.password 를 설정하면 자동으로 만듭니다.");
			return;
		}
		if (repository.existsByLoginId(loginId)) {
			log.warn("아이디 {} 가 이미 일반 회원으로 있어 관리자를 만들지 않았습니다.", loginId);
			return;
		}
		memberService.create(loginId, password, "관리자", Role.ADMIN);
		log.info("관리자 계정 {} 를 만들었습니다.", loginId);
	}
}
