package com.plantspot.recommend;

import java.time.LocalDateTime;

import com.plantspot.house.Room;
import com.plantspot.member.Member;
import com.plantspot.plant.PlantSpecies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 회원이 키우는 식물: 종, 별명, 사진, 현재 위치(방·평면도 좌표). */
@Entity
@Table(name = "my_plant")
public class MyPlant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_id")
	private PlantSpecies plant;

	@Column(nullable = false, length = 40)
	private String nickname;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id")
	private Room room;

	/** 방 평면도에서 현재 위치(cm) */
	@Column(name = "pos_x")
	private Double x;

	@Column(name = "pos_y")
	private Double y;

	/** 식물 사진(업로드 파일) 번호 */
	@Column(name = "photo_file_id")
	private Long photoFileId;

	@Column(length = 500)
	private String memo;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected MyPlant() {
	}

	public MyPlant(Member owner, PlantSpecies plant, String nickname) {
		this.owner = owner;
		this.plant = plant;
		this.nickname = nickname;
		this.createdAt = LocalDateTime.now();
	}

	public void update(PlantSpecies plant, String nickname, String memo, Long photoFileId) {
		this.plant = plant;
		this.nickname = nickname;
		this.memo = memo;
		this.photoFileId = photoFileId;
	}

	public void moveTo(Room room, Double x, Double y) {
		this.room = room;
		this.x = room == null ? null : x;
		this.y = room == null ? null : y;
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

	public PlantSpecies getPlant() {
		return plant;
	}

	public String getNickname() {
		return nickname;
	}

	public Room getRoom() {
		return room;
	}

	public Double getX() {
		return x;
	}

	public Double getY() {
		return y;
	}

	public Long getPhotoFileId() {
		return photoFileId;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
