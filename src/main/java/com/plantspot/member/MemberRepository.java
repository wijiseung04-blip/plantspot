package com.plantspot.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);

	long countByRole(Role role);

	List<Member> findAllByOrderByIdAsc();
}
