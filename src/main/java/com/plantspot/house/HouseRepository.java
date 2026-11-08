package com.plantspot.house;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseRepository extends JpaRepository<House, Long> {

	List<House> findByMemberIdOrderByIdAsc(Long memberId);
}
