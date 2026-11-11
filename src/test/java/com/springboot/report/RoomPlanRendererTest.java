package com.springboot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.springboot.calculator.SunlightCalculator;
import com.springboot.domain.Point;

/** 배치도 PNG 의 안쪽 창 선 (발코니 + 거실) */
class RoomPlanRendererTest {

	/** 가로 400 × 세로 600, 2번 벽(아래, y=600)이 바깥 창 벽 */
	static final List<Point> ROOM = List.of(new Point(0, 0), new Point(400, 0), new Point(400, 600), new Point(0, 600));

	@Test
	void 안쪽_창_선은_벽과_나란히_방_안쪽() {
		double[] line = RoomPlanRenderer.innerGlassLine(ROOM, new SunlightCalculator.InnerGlass(2, 150, 0.75));
		// 2번 벽 (400,600)→(0,600) 에서 안쪽(위)으로 150cm → y = 450
		assertThat(line[1]).isCloseTo(450, within(1e-9));
		assertThat(line[3]).isCloseTo(450, within(1e-9));
		assertThat(Math.abs(line[0] - line[2])).isCloseTo(400, within(1e-9));
		// 꼭짓점 순서를 반대로 해도 안쪽
		List<Point> reversed = List.of(new Point(0, 600), new Point(400, 600), new Point(400, 0), new Point(0, 0));
		assertThat(RoomPlanRenderer.innerGlassLine(reversed, new SunlightCalculator.InnerGlass(0, 150, 0.75))[1])
				.isCloseTo(450, within(1e-9));
		assertThat(RoomPlanRenderer.innerGlassLine(ROOM, null)).isNull();
		assertThat(RoomPlanRenderer.innerGlassLine(ROOM, new SunlightCalculator.InnerGlass(7, 150, 0.75))).isNull();
	}

	@Test
	void PNG에_파란_점선을_그린다() {
		BufferedImage with = RoomPlanRenderer.render(ROOM, 0, List.of(), new SunlightCalculator.InnerGlass(2, 150, 0.75),
				List.of(), 25, c -> Color.WHITE, List.of(), 600);
		BufferedImage without = RoomPlanRenderer.render(ROOM, 0, List.of(), List.of(), 25, c -> Color.WHITE, List.of(),
				600);
		assertThat(bluePixels(with)).isGreaterThan(50);
		assertThat(bluePixels(without)).isZero();
	}

	private static int bluePixels(BufferedImage img) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				Color c = new Color(img.getRGB(x, y));
				if (c.getBlue() > 180 && c.getRed() < 120 && c.getGreen() > 130 && c.getGreen() < 190) {
					n++;
				}
			}
		}
		return n;
	}
}
