package com.plantspot.backend.weather;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SunEventRepository extends JpaRepository<SunEvent, Long> {

	Optional<SunEvent> findByLocdateAndLatKeyAndLonKey(LocalDate locdate, double latKey, double lonKey);
}
