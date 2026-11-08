package com.plantspot.file;

import java.time.LocalDateTime;

import com.plantspot.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 업로드 파일 기록. 실제 파일은 app.upload-dir 아래에 storedName으로 저장한다. */
@Entity
@Table(name = "upload_file")
public class UploadFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member owner;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FileCategory category;

	@Column(name = "original_name", nullable = false)
	private String originalName;

	/** 저장 파일 이름(UUID.확장자) — 사용자가 준 이름은 경로에 쓰지 않는다 */
	@Column(name = "stored_name", nullable = false, unique = true)
	private String storedName;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(nullable = false)
	private long size;

	/** 관련된 방(평면도·센서 기록), 없으면 null */
	@Column(name = "room_id")
	private Long roomId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected UploadFile() {
	}

	public UploadFile(Member owner, FileCategory category, String originalName, String storedName,
			String contentType, long size, Long roomId) {
		this.owner = owner;
		this.category = category;
		this.originalName = originalName;
		this.storedName = storedName;
		this.contentType = contentType;
		this.size = size;
		this.roomId = roomId;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isOwnedBy(Long memberId) {
		return owner.getId().equals(memberId);
	}

	public Long getId() {
		return id;
	}

	public Member getOwner() {
		return owner;
	}

	public FileCategory getCategory() {
		return category;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getStoredName() {
		return storedName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSize() {
		return size;
	}

	public Long getRoomId() {
		return roomId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
