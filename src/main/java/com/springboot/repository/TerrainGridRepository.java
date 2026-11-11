package com.springboot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.domain.TerrainGrid;

public interface TerrainGridRepository extends JpaRepository<TerrainGrid, Long> {

	/** 가장 최근 격자 (격자는 보통 하나만 둔다) */
	Optional<TerrainGrid> findTopByOrderByIdDesc();
}
