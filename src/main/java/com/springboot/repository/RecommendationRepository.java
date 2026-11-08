package com.springboot.repository;

import com.springboot.domain.Recommendation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	List<Recommendation> findByRoomIdOrderByRankAscSeasonAsc(Long roomId);

	List<Recommendation> findByPlantId(Long plantId);

	default void deleteByRoom(Long roomId) {
		deleteAll(findByRoomIdOrderByRankAscSeasonAsc(roomId));
		flush();
	}
}
