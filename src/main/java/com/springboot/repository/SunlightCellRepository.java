package com.springboot.repository;

import com.springboot.domain.SeasonDay;
import com.springboot.domain.SunlightCell;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SunlightCellRepository extends JpaRepository<SunlightCell, Long> {

	List<SunlightCell> findByRoomIdAndSeasonAndYearOrderByIyAscIxAsc(Long roomId, SeasonDay season, int year);

	List<SunlightCell> findByRoomId(Long roomId);

	/** 엔티티 단위 삭제 (벌크 삭제는 SQLite 번호 재사용 시 영속성 컨텍스트와 충돌). */
	default void deleteByRoom(Long roomId) {
		deleteAll(findByRoomId(roomId));
		flush();
	}
}
