package com.plantspot.plant;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long> {

	Optional<PlantSpecies> findByCntntsNo(String cntntsNo);

	/** 이름(한글·학명·영문) 검색 + 광요구도 코드 + 관리 수준 코드 거르기 (각 조건은 null이면 무시). */
	@Query("""
			select p from PlantSpecies p
			where (:keyword is null or p.name like concat('%', :keyword, '%')
			       or lower(p.scientificName) like lower(concat('%', :keyword, '%'))
			       or lower(p.englishName) like lower(concat('%', :keyword, '%')))
			  and (:light is null or p.lightCodes like concat('%', :light, '%'))
			  and (:level is null or p.manageLevelCode = :level)
			order by p.name
			""")
	Page<PlantSpecies> search(@Param("keyword") String keyword, @Param("light") String light,
			@Param("level") String level, Pageable pageable);
}
