package com.springboot.dto;

import com.springboot.domain.CurtainType;
import com.springboot.domain.Point;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 집·방·창문 요청 본문. [필수: Bean Validation] */
public final class HouseRequests {

	private HouseRequests() {
	}

	public record CreateHouse(
			@NotBlank(message = "동을 선택하세요.") String dong,
			@NotBlank(message = "지번을 입력하세요.") String jibun,
			@NotBlank(message = "집 이름을 입력하세요.") @Size(max = 40, message = "집 이름은 40자 이하입니다.") String name,
			@NotNull(message = "층을 입력하세요.") @Min(value = 1, message = "층은 1 이상입니다.")
			@Max(value = 80, message = "층은 80 이하입니다.") Integer floor,
			@DecimalMin(value = "2.2", message = "층 높이는 2.2m 이상입니다.")
			@DecimalMax(value = "6", message = "층 높이는 6m 이하입니다.") Double floorHeight,
			/** 같은 지번에 건물이 여러 동일 때 고른 건물 (없으면 가장 높은 건물) */
			Long buildingId,
			/** 동 안 세대 위치 −1 ~ 1 (없으면 가운데) */
			@DecimalMin(value = "-1", message = "세대 위치는 -1~1입니다.")
			@DecimalMax(value = "1", message = "세대 위치는 -1~1입니다.") Double unitPosition) {
	}

	public record UpdateHouse(
			@NotBlank(message = "집 이름을 입력하세요.") @Size(max = 40, message = "집 이름은 40자 이하입니다.") String name,
			@NotNull(message = "층을 입력하세요.") @Min(value = 1, message = "층은 1 이상입니다.")
			@Max(value = 80, message = "층은 80 이하입니다.") Integer floor,
			@DecimalMin(value = "2.2", message = "층 높이는 2.2m 이상입니다.")
			@DecimalMax(value = "6", message = "층 높이는 6m 이하입니다.") Double floorHeight,
			/** 동 안 세대 위치 −1 ~ 1 (보내지 않으면 그대로) */
			@DecimalMin(value = "-1", message = "세대 위치는 -1~1입니다.")
			@DecimalMax(value = "1", message = "세대 위치는 -1~1입니다.") Double unitPosition) {
	}

	public record SaveRoom(
			@NotBlank(message = "방 이름을 입력하세요.") @Size(max = 40, message = "방 이름은 40자 이하입니다.") String name,
			@NotNull(message = "방 모양을 입력하세요.") @Size(min = 3, max = 30, message = "방 모양은 점 3~30개입니다.")
			List<@Valid @NotNull Point> shape,
			@NotNull(message = "방향을 입력하세요.") @DecimalMin(value = "0", message = "방향은 0~360도입니다.")
			@DecimalMax(value = "360", message = "방향은 0~360도입니다.") Double orientationDeg,
			@DecimalMin(value = "180", message = "천장 높이는 180cm 이상입니다.")
			@DecimalMax(value = "500", message = "천장 높이는 500cm 이하입니다.") Double ceilingHeight,
			CurtainType curtain,
			@DecimalMin(value = "-20", message = "겨울 최저 실내 온도는 -20~35℃입니다.")
			@DecimalMax(value = "35", message = "겨울 최저 실내 온도는 -20~35℃입니다.") Double winterMinTemp) {
	}

	public record SaveWindow(
			@NotNull(message = "벽을 선택하세요.") @Min(value = 0, message = "벽 번호가 잘못되었습니다.") Integer wallIndex,
			@NotNull(message = "벽 시작점에서의 거리를 입력하세요.")
			@DecimalMin(value = "0", message = "거리는 0 이상입니다.") Double offset,
			@NotNull(message = "창 폭을 입력하세요.") @DecimalMin(value = "20", message = "창 폭은 20cm 이상입니다.")
			@DecimalMax(value = "1500", message = "창 폭은 1500cm 이하입니다.") Double width,
			@NotNull(message = "창 높이를 입력하세요.") @DecimalMin(value = "20", message = "창 높이는 20cm 이상입니다.")
			@DecimalMax(value = "450", message = "창 높이는 450cm 이하입니다.") Double height,
			@NotNull(message = "창턱 높이를 입력하세요.") @DecimalMin(value = "0", message = "창턱 높이는 0 이상입니다.")
			@DecimalMax(value = "300", message = "창턱 높이는 300cm 이하입니다.") Double sillHeight) {
	}
}
