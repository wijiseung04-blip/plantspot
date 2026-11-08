package com.springboot.calculator;

import com.springboot.domain.HeightSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HeightRuleTest {

	@Test
	void 정상_높이는_그대로() {
		HeightRule.Result r = HeightRule.decide(18.75, 6, "공동주택");
		assertThat(r.source()).isEqualTo(HeightSource.MEASURED);
		assertThat(r.height()).isEqualTo(18.75);
	}

	@Test
	void 층수_없이_높이만_있어도_그대로() {
		assertThat(HeightRule.decide(7.2, null, null).source()).isEqualTo(HeightSource.MEASURED);
	}

	@Test
	void 높이_15400m_같은_입력_오류는_층수로_추정() {
		HeightRule.Result r = HeightRule.decide(15400.0, 5, "공동주택");
		assertThat(r.source()).isEqualTo(HeightSource.FLOOR_ESTIMATE);
		assertThat(r.height()).isCloseTo(15.0, within(1e-9));
	}

	@Test
	void 층당_10m가_넘으면_층수로_추정() {
		HeightRule.Result r = HeightRule.decide(45.0, 2, "단독주택");
		assertThat(r.source()).isEqualTo(HeightSource.FLOOR_ESTIMATE);
		assertThat(r.height()).isCloseTo(7.0, within(1e-9));
	}

	@Test
	void 높이_없으면_용도별_층고() {
		assertThat(HeightRule.decide(null, 10, "공동주택").height()).isCloseTo(30.0, within(1e-9));
		assertThat(HeightRule.decide(0.0, 2, "단독주택").height()).isCloseTo(7.0, within(1e-9));
		assertThat(HeightRule.decide(null, 3, "제2종근린생활시설").height()).isCloseTo(10.8, within(1e-9));
	}

	@Test
	void 둘_다_없으면_기본_3_5m() {
		HeightRule.Result r = HeightRule.decide(null, 0, null);
		assertThat(r.source()).isEqualTo(HeightSource.DEFAULT);
		assertThat(r.height()).isEqualTo(3.5);
	}
}
