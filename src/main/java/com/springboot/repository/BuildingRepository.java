package com.springboot.repository;

import com.springboot.domain.Building;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository extends JpaRepository<Building, Long> {

	/** 동·지번으로 찾기. 한 지번에 여러 동이 있을 수 있어 면적 큰 순으로 돌려준다. */
	List<Building> findByDongAndJibunOrderByAreaDesc(String dong, String jibun);

	/** 중심점이 사각형 범위 안에 있는 건물 (EPSG:5186). */
	@Query("""
			select b from Building b
			where b.centerX between :minX and :maxX
			  and b.centerY between :minY and :maxY
			""")
	List<Building> findInBox(@Param("minX") double minX, @Param("maxX") double maxX,
			@Param("minY") double minY, @Param("maxY") double maxY);

	/** 건물 중심점의 범위 [minX, maxX, minY, maxY] (지형 격자 범위 정하기). */
	@Query("select min(b.centerX), max(b.centerX), min(b.centerY), max(b.centerY) from Building b")
	List<Object[]> findExtent();

	/** 화면 동 선택 목록. */
	@Query("select distinct b.dong from Building b where b.dong is not null order by b.dong")
	List<String> findDistinctDongs();
}
