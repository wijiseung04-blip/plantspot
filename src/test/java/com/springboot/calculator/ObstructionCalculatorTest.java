package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.springboot.calculator.ObstructionCalculator.Obstacle;
import com.springboot.calculator.ObstructionCalculator.Skyline;

class ObstructionCalculatorTest {

	/** 관측점 (1000, 1000). 남쪽 20m에 폭 40m, 높이 21.2m 건물 → 눈 높이 1.2m 기준 정남 가림 각도 atan(20/20) = 45° */
	private static final String SOUTH_WALL = "MULTIPOLYGON (((980 970, 1020 970, 1020 980, 980 980, 980 970)))";

	@Test
	void 정남_건물의_가림_각도() {
		Skyline sky = ObstructionCalculator.calculate(1000, 1000, 1.2,
				List.of(new Obstacle(7, 21.2, 1000, 975, SOUTH_WALL)));
		int south = ObstructionCalculator.binOf(180);
		assertThat(sky.angles()[south]).isCloseTo(45.0, within(0.5));
		assertThat(sky.causeIds()[south]).isEqualTo(7);
		assertThat(sky.distances()[south]).isCloseTo(20.0, within(0.3));
		// 북쪽은 열려 있다
		assertThat(sky.angles()[ObstructionCalculator.binOf(0)]).isZero();
		// 건물 끝(±20m, 거리 20m → 방위 135°·225°) 근처까지만 가린다
		assertThat(sky.angles()[ObstructionCalculator.binOf(140)]).isPositive();
		assertThat(sky.angles()[ObstructionCalculator.binOf(120)]).isZero();
	}

	@Test
	void 눈보다_낮은_건물은_가리지_않는다() {
		Skyline sky = ObstructionCalculator.calculate(1000, 1000, 30,
				List.of(new Obstacle(7, 21.2, 1000, 975, SOUTH_WALL)));
		for (double a : sky.angles()) {
			assertThat(a).isZero();
		}
	}

	@Test
	void 칸마다_가장_높은_가림물이_남는다() {
		Skyline sky = ObstructionCalculator.calculate(1000, 1000, 1.2, List.of(
				new Obstacle(1, 11.2, 1000, 975, SOUTH_WALL),
				new Obstacle(2, 101.2, 1000, 900, null))); // 정남 100m, 외곽선 없음 → 중심점, atan(100/100)=45°
		int south = ObstructionCalculator.binOf(180);
		assertThat(sky.causeIds()[south]).isEqualTo(2);
		assertThat(sky.angles()[south]).isCloseTo(45.0, within(1e-6));
	}

	@Test
	void 칸_번호() {
		assertThat(ObstructionCalculator.binOf(0)).isZero();
		assertThat(ObstructionCalculator.binOf(359.9)).isEqualTo(71);
		assertThat(ObstructionCalculator.binOf(-5)).isEqualTo(71);
		assertThat(ObstructionCalculator.binOf(182)).isEqualTo(36);
	}

	@Test
	void WKT_읽기() {
		List<List<double[]>> rings = Wkt.rings(
				"MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0), (0.1 0.1, 0.2 0.1, 0.1 0.1)), ((5 5, 6 5, 6 6, 5 5)))");
		assertThat(rings).hasSize(3);
		assertThat(rings.get(2).get(1)).containsExactly(6, 5);
		assertThat(Wkt.rings("MULTIPOLYGON EMPTY")).isEmpty();
		assertThat(Wkt.rings(null)).isEmpty();
	}

	@Test
	void 프로필은_실측값이_있으면_실측값을_쓴다() {
		ObstructionProfile p = ObstructionProfile.empty();
		p.building()[ObstructionCalculator.binOf(180)] = 30;
		assertThat(p.angleAt(181)).isEqualTo(30);
		p.sensor()[ObstructionCalculator.binOf(180)] = 42;
		assertThat(p.angleAt(181)).isEqualTo(42);
		assertThat(p.buildingAngleAt(181)).isEqualTo(30);
		assertThat(p.hasSensorData()).isTrue();
		assertThat(p.withoutSensor().angleAt(181)).isEqualTo(30);
	}
}
