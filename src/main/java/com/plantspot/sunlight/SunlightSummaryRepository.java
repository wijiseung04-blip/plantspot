package com.plantspot.sunlight;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SunlightSummaryRepository extends JpaRepository<SunlightSummary, Long> {

	Optional<SunlightSummary> findByRoomIdAndSeasonAndYear(Long roomId, SeasonDay season, int year);

	List<SunlightSummary> findByRoomId(Long roomId);

	default void deleteByRoom(Long roomId) {
		deleteAll(findByRoomId(roomId));
		flush();
	}
}
