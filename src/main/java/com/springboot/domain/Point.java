package com.springboot.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** 방 평면도 좌표(cm). 캔버스와 같이 x는 오른쪽, y는 아래쪽이 +. */
public record Point(
		@DecimalMin(value = "0", message = "좌표는 0 이상이어야 합니다.")
		@DecimalMax(value = "3000", message = "좌표는 3000cm 이하여야 합니다.") double x,
		@DecimalMin(value = "0", message = "좌표는 0 이상이어야 합니다.")
		@DecimalMax(value = "3000", message = "좌표는 3000cm 이하여야 합니다.") double y) {
}
