package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BuildingFacadeTest {

	/** 동서 60m × 남북 12m 판상형 아파트, 중심 (1000, 1000) */
	static final String SLAB = "MULTIPOLYGON(((970 994, 1030 994, 1030 1006, 970 1006, 970 994)))";

	@Test
	void 긴_축() {
		assertThat(BuildingFacade.longAxis(SLAB)).isEqualTo(90.0);
		assertThat(BuildingFacade.longAxis("MULTIPOLYGON(((994 970, 1006 970, 1006 1030, 994 1030, 994 970)))"))
				.isEqualTo(0.0);
		assertThat(BuildingFacade.longAxis(null)).isNaN();
	}

	@Test
	void 남쪽_창은_남쪽_외벽_바로_앞() {
		BuildingFacade.Observer o = BuildingFacade.observer(SLAB, 1000, 1000, 180, 0);
		assertThat(o.x()).isCloseTo(1000, within(1e-6));
		assertThat(o.y()).isCloseTo(993.5, within(1e-6));
	}

	@Test
	void 세대_위치를_긴_축_따라_옮긴다() {
		// 긴 축 90°(동쪽) → +1 = 동쪽 끝, −1 = 서쪽 끝
		assertThat(BuildingFacade.observer(SLAB, 1000, 1000, 180, 1).x()).isCloseTo(1028.5, within(1e-6));
		assertThat(BuildingFacade.observer(SLAB, 1000, 1000, 180, -1).x()).isCloseTo(971.5, within(1e-6));
		// 동쪽 끝 세대의 동쪽 창은 동쪽 끝 벽 앞
		BuildingFacade.Observer east = BuildingFacade.observer(SLAB, 1000, 1000, 90, 1);
		assertThat(east.x()).isCloseTo(1030.5, within(1e-6));
	}

	@Test
	void 외곽선이_없으면_중심() {
		BuildingFacade.Observer o = BuildingFacade.observer(null, 5, 6, 180, 0.5);
		assertThat(o.x()).isEqualTo(5);
		assertThat(o.y()).isEqualTo(6);
	}

	@Test
	void 오목한_건물() {
		// ㄷ자: 남쪽으로 열린 마당. 몸통 가운데에서 남쪽 창 → 마당 건너 날개가 아니라 몸통의 남쪽 벽(마당 쪽)
		String u = "MULTIPOLYGON(((970 940, 980 940, 980 995, 1040 995, 1040 940, 1050 940, 1050 1010, 970 1010, 970 940)))";
		BuildingFacade.Observer o = BuildingFacade.observer(u, 1000, 1000, 180, 0);
		assertThat(o.y()).isCloseTo(994.5, within(1e-6));
		// 서쪽 날개 세대(−1)의 남쪽 창은 날개 끝 벽 앞
		assertThat(BuildingFacade.observer(u, 1000, 1000, 180, -1).y()).isCloseTo(939.5, within(1e-6));
	}
}
