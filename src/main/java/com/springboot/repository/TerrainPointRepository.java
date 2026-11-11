package com.springboot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.domain.TerrainPoint;

public interface TerrainPointRepository extends JpaRepository<TerrainPoint, Long> {

	/** 계산용으로 격자 전체를 한 번에 읽는다: [ix, iy, elevation] */
	@Query("select p.ix, p.iy, p.elevation from TerrainPoint p where p.grid.id = :gridId")
	List<Object[]> findValues(@Param("gridId") Long gridId);

	long countByGridId(Long gridId);

	List<TerrainPoint> findByGridId(Long gridId);
}
