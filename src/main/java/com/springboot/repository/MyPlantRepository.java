package com.springboot.repository;

import com.springboot.domain.MyPlant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MyPlantRepository extends JpaRepository<MyPlant, Long> {

	List<MyPlant> findByOwnerIdOrderByIdAsc(Long ownerId);

	List<MyPlant> findByRoomId(Long roomId);

	List<MyPlant> findByPlantId(Long plantId);
}
