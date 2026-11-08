package com.plantspot.backend.house;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShapeTest {

	/** 가로 400, 세로 300 직사각형: 0번 위, 1번 오른쪽, 2번 아래, 3번 왼쪽 벽. */
	private static final List<Point> RECT = List.of(new Point(0, 0), new Point(400, 0), new Point(400, 300),
			new Point(0, 300));

	@Test
	void 위쪽이_북쪽이면_벽_방향은_북_동_남_서() {
		assertThat(Shape.wallAzimuth(RECT, 0, 0)).isCloseTo(0, within(1e-9));
		assertThat(Shape.wallAzimuth(RECT, 1, 0)).isCloseTo(90, within(1e-9));
		assertThat(Shape.wallAzimuth(RECT, 2, 0)).isCloseTo(180, within(1e-9));
		assertThat(Shape.wallAzimuth(RECT, 3, 0)).isCloseTo(270, within(1e-9));
	}

	@Test
	void 점_순서가_반대여도_바깥쪽을_향한다() {
		List<Point> reversed = List.of(new Point(0, 0), new Point(0, 300), new Point(400, 300), new Point(400, 0));
		// 0번 벽 = 왼쪽 벽(서), 1번 벽 = 아래 벽(남)
		assertThat(Shape.wallAzimuth(reversed, 0, 0)).isCloseTo(270, within(1e-9));
		assertThat(Shape.wallAzimuth(reversed, 1, 0)).isCloseTo(180, within(1e-9));
	}

	@Test
	void 평면도를_돌리면_방향도_돈다() {
		// 위쪽이 동쪽(90°)이면 아래 벽은 서쪽
		assertThat(Shape.wallAzimuth(RECT, 2, 90)).isCloseTo(270, within(1e-9));
		assertThat(Shape.wallAzimuth(RECT, 0, 315)).isCloseTo(315, within(1e-9));
	}

	@Test
	void 넓이와_벽_길이() {
		assertThat(Math.abs(Shape.signedArea(RECT))).isEqualTo(120_000);
		assertThat(Shape.wallLength(RECT, 1)).isEqualTo(300);
	}

	@Test
	void 방위_이름() {
		assertThat(Shape.directionName(180)).isEqualTo("남향");
		assertThat(Shape.directionName(150)).isEqualTo("남동향");
		assertThat(Shape.directionName(350)).isEqualTo("북향");
	}

	@Test
	void 꼬인_다각형은_단순하지_않다() {
		assertThat(Shape.isSimple(RECT)).isTrue();
		List<Point> bowtie = List.of(new Point(0, 0), new Point(400, 300), new Point(400, 0), new Point(0, 300));
		assertThat(Shape.isSimple(bowtie)).isFalse();
		List<Point> lShape = List.of(new Point(0, 0), new Point(400, 0), new Point(400, 200), new Point(200, 200),
				new Point(200, 400), new Point(0, 400));
		assertThat(Shape.isSimple(lShape)).isTrue();
	}

	@Test
	void 저장_형식_왕복() {
		assertThat(Shape.decode(Shape.encode(RECT))).isEqualTo(RECT);
	}
}
