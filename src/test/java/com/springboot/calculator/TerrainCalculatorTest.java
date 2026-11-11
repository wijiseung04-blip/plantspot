package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TerrainCalculatorTest {

	/** 20km × 20km, 90m 간격, 관측점 (10000, 10000). heightFn(x, y) */
	private static TerrainModel model(java.util.function.DoubleBinaryOperator heightFn) {
		int n = 223;
		TerrainModel m = new TerrainModel(0, 0, 90, n, n);
		for (int iy = 0; iy < n; iy++) {
			for (int ix = 0; ix < n; ix++) {
				m.set(ix, iy, heightFn.applyAsDouble(ix * 90, iy * 90));
			}
		}
		return m;
	}

	@Test
	void 보간() {
		TerrainModel m = model((x, y) -> x * 0.1);
		assertThat(m.elevationAt(45, 45)).isCloseTo(4.5, within(1e-9));
		assertThat(m.elevationAt(-1, 0)).isNaN();
	}

	@Test
	void 남쪽_3km_높이_500m_산() {
		// y < 7000 (3km 남쪽부터) 높이 500m 평평한 산
		TerrainModel m = model((x, y) -> y < 7000 ? 500 : 0);
		TerrainCalculator.TerrainSkyline s = TerrainCalculator.calculate(m, 10000, 10000, 1.2, 300, 10000);
		int south = ObstructionCalculator.binOf(180);
		// atan((500 − 1.2 − 곡률 0.6) / 약 3000) ≈ 9.4°
		assertThat(s.angles()[south]).isCloseTo(9.4, within(0.4));
		assertThat(s.distances()[south]).isCloseTo(3000, within(100.0));
		assertThat(s.angles()[ObstructionCalculator.binOf(0)]).isZero(); // 북쪽은 평지
		// 동쪽(90°)은 산이 비스듬히 멀리 있어 낮다
		assertThat(s.angles()[ObstructionCalculator.binOf(90)]).isLessThan(1);
	}

	@Test
	void 높은_층에서는_낮아지고_멀면_곡률로_조금_낮아진다() {
		TerrainModel m = model((x, y) -> y < 7000 ? 500 : 0);
		double low = TerrainCalculator.calculate(m, 10000, 10000, 1.2, 300, 10000).angles()[36];
		double high = TerrainCalculator.calculate(m, 10000, 10000, 61.2, 300, 10000).angles()[36];
		assertThat(high).isLessThan(low);
		assertThat(high).isCloseTo(Math.toDegrees(Math.atan((500 - 61.2 - 0.6) / 3000)), within(0.4));
	}

	@Test
	void 가까운_곳은_보지_않는다() {
		// 100~200m 앞 언덕은 건물 데이터 반경(300m) 안이라 지형으로는 세지 않는다
		TerrainModel m = model((x, y) -> y > 9800 && y < 9900 ? 100 : 0);
		assertThat(TerrainCalculator.calculate(m, 10000, 10000, 1.2, 300, 10000).angles()[36]).isZero();
	}
}
