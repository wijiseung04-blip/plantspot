package com.springboot.repository;

import com.springboot.domain.FileCategory;
import com.springboot.domain.UploadFile;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

	List<UploadFile> findByOwnerIdOrderByIdDesc(Long ownerId);

	List<UploadFile> findByOwnerIdAndCategoryOrderByIdDesc(Long ownerId, FileCategory category);
}
