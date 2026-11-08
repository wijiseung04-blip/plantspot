package com.plantspot.file;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

	List<UploadFile> findByOwnerIdOrderByIdDesc(Long ownerId);

	List<UploadFile> findByOwnerIdAndCategoryOrderByIdDesc(Long ownerId, FileCategory category);
}
