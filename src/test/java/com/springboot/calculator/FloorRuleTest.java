package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.springboot.domain.Building;
import com.springboot.domain.HeightSource;

class FloorRuleTest {

	private static Building building(Integer floors, double height, HeightSource source) {
		Building b = org.mockito.Mockito.mock(Building.class);
		org.mockito.Mockito.when(b.getGroundFloors()).thenReturn(floors);
		org.mockito.Mockito.when(b.getHeight()).thenReturn(height);
		org.mockito.Mockito.when(b.getHeightSource()).thenReturn(source);
		org.mockito.Mockito.when(b.getDongLabel()).thenReturn("101동");
		return b;
	}

	@Test
	void 지상층수가_있으면_그_값() {
		Building b = building(25, 67.86, HeightSource.MEASURED);
		assertThat(FloorRule.maxFloor(b)).isEqualTo(25);
		assertThat(FloorRule.violation(b, 25)).isNull();
		assertThat(FloorRule.violation(b, 26)).contains("101동").contains("지상 25층");
		assertThat(FloorRule.warning(b)).isNull();
	}

	@Test
	void 층수가_없고_실측_높이면_높이로_짐작() {
		// 18.75m ÷ 2.5 = 7.5 → 8층까지
		Building b = building(null, 18.75, HeightSource.MEASURED);
		assertThat(FloorRule.maxFloor(b)).isEqualTo(8);
		assertThat(FloorRule.violation(b, 9)).contains("18.8m");
		// 딱 나누어떨어지면 그 값
		assertThat(FloorRule.maxFloor(building(0, 10.0, HeightSource.MEASURED))).isEqualTo(4);
	}

	@Test
	void 둘_다_없으면_막지_않고_경고() {
		Building b = building(null, 3.5, HeightSource.DEFAULT);
		assertThat(FloorRule.maxFloor(b)).isNull();
		assertThat(FloorRule.violation(b, 30)).isNull();
		assertThat(FloorRule.warning(b)).contains("층수 정보가 없어");
		assertThat(FloorRule.maxFloor(null)).isNull();
	}
}
