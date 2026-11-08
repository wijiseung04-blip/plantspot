package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.springboot.calculator.TmConverter.LatLon;
import com.springboot.calculator.TmConverter.Tm;

class TmConverterTest {

	@Test
	void 원점_200000_600000은_북위38_동경127() {
		LatLon ll = TmConverter.toLatLon(200000, 600000);
		assertThat(ll.latitude()).isCloseTo(38.0, within(1e-9));
		assertThat(ll.longitude()).isCloseTo(127.0, within(1e-9));
	}

	@Test
	void 하단동_1160_6_중심점() {
		LatLon ll = TmConverter.toLatLon(378731.47, 281659.43);
		assertThat(ll.latitude()).isCloseTo(35.1154, within(0.0001));
		assertThat(ll.longitude()).isCloseTo(128.9605, within(0.0001));
	}

	@Test
	void 위경도로_갔다가_돌아오면_1cm_이내() {
		Tm tm = TmConverter.fromLatLon(35.1154, 128.9605);
		LatLon back = TmConverter.toLatLon(tm.x(), tm.y());
		Tm again = TmConverter.fromLatLon(back.latitude(), back.longitude());
		assertThat(again.x()).isCloseTo(tm.x(), within(0.01));
		assertThat(again.y()).isCloseTo(tm.y(), within(0.01));
		assertThat(tm.x()).isCloseTo(378731.47, within(10.0));
		assertThat(tm.y()).isCloseTo(281659.43, within(10.0));
	}
}
