package com.plantspot.plant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlantConverterTest {

	@Test
	void 낮음_중간_광도는_300에서_1500_lux_직사광_최대_3시간() {
		var c = PlantConverter.convert("055001,055002", "064003,064002", "057003");
		assertThat(c.minLux()).isEqualTo(300);
		assertThat(c.maxLux()).isEqualTo(1500);
		assertThat(c.minDirectMinutes()).isZero();
		assertThat(c.maxDirectMinutes()).isEqualTo(180);
		assertThat(c.minWindowDistance()).isEqualTo(150);
		assertThat(c.maxWindowDistance()).isEqualTo(500);
		assertThat(c.winterMinTemp()).isEqualTo(7);
	}

	@Test
	void 높은_광도만이면_직사광_2시간_이상() {
		var c = PlantConverter.convert("055003", "064005", "057001");
		assertThat(c.minLux()).isEqualTo(1500);
		assertThat(c.minDirectMinutes()).isEqualTo(120);
		assertThat(c.maxDirectMinutes()).isNull();
		assertThat(c.maxWindowDistance()).isEqualTo(50);
		assertThat(c.winterMinTemp()).isZero();
	}

	@Test
	void 낮은_광도만이면_직사광은_1시간까지() {
		assertThat(PlantConverter.convert("055001", null, null).maxDirectMinutes()).isEqualTo(60);
	}

	@Test
	void 거리와_무관한_배치_코드는_무시하고_코드가_없으면_기본값() {
		var c = PlantConverter.convert(null, "064006,064008", null);
		assertThat(c.minWindowDistance()).isNull();
		assertThat(c.minLux()).isEqualTo(300);
		assertThat(c.maxLux()).isEqualTo(10000);
		assertThat(c.winterMinTemp()).isNull();
	}
}
