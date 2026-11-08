package com.springboot.repository;

import com.springboot.domain.WeatherHour;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherHourRepository extends JpaRepository<WeatherHour, Long> {

	List<WeatherHour> findByStationIdAndObservedAtBetweenOrderByObservedAtAsc(int stationId, LocalDateTime from,
			LocalDateTime to);

	Optional<WeatherHour> findByStationIdAndObservedAt(int stationId, LocalDateTime observedAt);

	long countByStationId(int stationId);
}
