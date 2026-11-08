package com.plantspot.sunlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.plantspot.house.Point;
import com.plantspot.obstruction.ObstructionCalculator;
import com.plantspot.obstruction.ObstructionProfile;
import com.plantspot.sun.SolarCalculator;
import com.plantspot.sun.SolarPosition;
import com.plantspot.sunlight.SunlightCalculator.Cell;
import com.plantspot.sunlight.SunlightCalculator.Options;
import com.plantspot.sunlight.SunlightCalculator.Result;
import com.plantspot.sunlight.SunlightCalculator.RoomGeom;

class SunlightCalculatorTest {

	private static final double LAT = 35.1154;
	private static final double LON = 128.9605;

	/** 가로 400 × 세로 300 방, 위쪽 = 북. 2번 벽(아래) = 남쪽 벽. */
	private static final List<Point> RECT = List.of(new Point(0, 0), new Point(400, 0), new Point(400, 300),
			new Point(0, 300));

	private final SunlightCalculator calc = new SunlightCalculator(new SolarCalculator());

	/** 남쪽 벽 가운데 폭 200, 높이 150, 창턱 60 창. (2번 벽은 (400,300)→(0,300) 방향이므로 offset 100 = x 300~100) */
	private static RoomGeom southRoom(double orientation) {
		return SunlightCalculator.room(RECT, orientation, 230,
				List.of(new double[] { 1, 2, 100, 200, 150, 60, normalize(180 + orientation) }), 1.0);
	}

	private static double normalize(double d) {
		return ((d % 360) + 360) % 360;
	}

	private static int maxDepth(Result r) {
		// 직사광이 든 칸 중 남쪽 벽(y=300)에서 가장 먼 거리
		return (int) r.cells().stream().filter(c -> c.directMinutes() > 0).mapToDouble(c -> 300 - c.y()).max().orElse(0);
	}

	@Test
	void 정남_45도_해의_투영() {
		RoomGeom room = southRoom(0);
		double[][] quad = SunlightCalculator.sunPatch(room, room.windows().get(0), new SolarPosition(45, 180), null, 0);
		// 창턱 60cm → 60cm 안쪽, 창 위 210cm → 210cm 안쪽
		assertThat(quad[0][1]).isCloseTo(240, within(1e-6));
		assertThat(quad[2][1]).isCloseTo(90, within(1e-6));
		assertThat(quad[0][0]).isCloseTo(300, within(1e-6));
		assertThat(quad[1][0]).isCloseTo(100, within(1e-6));
	}

	@Test
	void 해가_창_뒤에_있거나_가려지면_빛이_없다() {
		RoomGeom room = southRoom(0);
		assertThat(SunlightCalculator.sunPatch(room, room.windows().get(0), new SolarPosition(30, 10), null, 0)).isNull();
		ObstructionProfile wall = ObstructionProfile.empty();
		wall.building()[ObstructionCalculator.binOf(180)] = 40;
		assertThat(SunlightCalculator.sunPatch(room, room.windows().get(0), new SolarPosition(31, 180), wall, 0)).isNull();
		assertThat(SunlightCalculator.sunPatch(room, room.windows().get(0), new SolarPosition(45, 180), wall, 0)).isNotNull();
	}

	@Test
	void 겨울_해는_방_깊숙이_여름_해는_창가에만() {
		Result winter = calc.calculate(southRoom(0), LAT, LON, SeasonDay.WINTER_SOLSTICE.date(2026),
				ObstructionProfile.empty(), Options.defaults());
		Result summer = calc.calculate(southRoom(0), LAT, LON, SeasonDay.SUMMER_SOLSTICE.date(2026),
				ObstructionProfile.empty(), Options.defaults());
		assertThat(maxDepth(winter)).isGreaterThan(250); // 동지 정오 31.5° → 창 위 210cm가 약 340cm 안쪽까지
		assertThat(maxDepth(summer)).isLessThan(120);
		assertThat(winter.windowSunMinutes().get(0)).isGreaterThan(summer.windowSunMinutes().get(0));
		assertThat(winter.daylightMinutes()).isLessThan(summer.daylightMinutes());
	}

	@Test
	void 앞_건물이_40도로_막으면_동지에는_직사광이_없다() {
		ObstructionProfile wall = ObstructionProfile.empty();
		java.util.Arrays.fill(wall.building(), 40);
		Result winter = calc.calculate(southRoom(0), LAT, LON, SeasonDay.WINTER_SOLSTICE.date(2026), wall,
				Options.defaults());
		assertThat(winter.maxDirectMinutes()).isZero();
		Result open = calc.calculate(southRoom(0), LAT, LON, SeasonDay.WINTER_SOLSTICE.date(2026),
				ObstructionProfile.empty(), Options.defaults());
		// 가림이 있으면 하늘빛도 줄어든다
		double litBlocked = winter.cells().stream().mapToDouble(Cell::lux).average().orElse(0);
		double litOpen = open.cells().stream().mapToDouble(Cell::lux).average().orElse(0);
		assertThat(litBlocked).isLessThan(litOpen);
	}

	@Test
	void 북향_창은_동지에_직사광이_없다() {
		// 평면도를 180° 돌리면 2번 벽이 북향
		Result r = calc.calculate(southRoom(180), LAT, LON, SeasonDay.WINTER_SOLSTICE.date(2026),
				ObstructionProfile.empty(), Options.defaults());
		assertThat(r.maxDirectMinutes()).isZero();
	}

	@Test
	void 창에서_멀수록_어둡다() {
		Result r = calc.calculate(southRoom(0), LAT, LON, SeasonDay.SPRING_EQUINOX.date(2026),
				ObstructionProfile.empty(), Options.defaults());
		// 창 바로 아래 바닥은 빛이 가파르게 들어와 오히려 덜 밝다 → 60cm 안쪽 칸과 비교
		Cell near = r.cells().stream().filter(c -> c.x() == 187.5 && c.y() == 237.5).findFirst().orElseThrow();
		Cell far = r.cells().stream().filter(c -> c.x() == 187.5).max(Comparator.comparingDouble(c -> 300 - c.y())).orElseThrow();
		assertThat(near.lux()).isGreaterThan(far.lux() * 3);
		assertThat(near.distanceToWindow()).isLessThan(far.distanceToWindow());
		assertThat(near.level()).isEqualTo(LightLevel.BRIGHT);
		// 창가 주광률은 수 %~수십 % 범위 (현실적인 크기인지 확인)
		assertThat(near.daylightFactor()).isBetween(0.02, 0.5);
		assertThat(far.daylightFactor()).isBetween(0.002, 0.05);
		assertThat(r.cells()).hasSize(16 * 12);
	}

	@Test
	void 커튼이_있으면_어두워진다() {
		RoomGeom open = southRoom(0);
		RoomGeom curtain = new RoomGeom(open.polygon(), open.orientationDeg(), open.ceilingHeight(), open.windows(), 0.2);
		LocalDate d = SeasonDay.SPRING_EQUINOX.date(2026);
		double a = calc.calculate(open, LAT, LON, d, null, Options.defaults()).cells().get(0).lux();
		double b = calc.calculate(curtain, LAT, LON, d, null, Options.defaults()).cells().get(0).lux();
		assertThat(b).isCloseTo(a * 0.2, within(2.0));
	}

	@Test
	void ㄱ자_방의_모서리_뒤는_직사광이_들지_않는다() {
		// 위쪽 날개의 오른쪽 위 구석(187.5, 37.5)은 동쪽 창과의 사이를 x=200 벽이 막는다
		// ㄱ자: 아래쪽 가로 400 × 세로 150 + 왼쪽 위 200 × 150. 창은 오른쪽 벽(동쪽)에 있음.
		List<Point> l = List.of(new Point(0, 0), new Point(200, 0), new Point(200, 150), new Point(400, 150),
				new Point(400, 300), new Point(0, 300));
		// 3번 벽 (400,150)→(400,300) = 동쪽 벽, 창 폭 150 전체
		RoomGeom room = SunlightCalculator.room(l, 0, 230, List.of(new double[] { 1, 3, 0, 150, 150, 50, 90 }), 1);
		// 아침 낮은 동쪽 해: 빛은 서쪽으로 길게 들어오지만 위쪽 날개(y<150)는 벽에 가려진다
		Result r = calc.calculate(room, LAT, LON, SeasonDay.SPRING_EQUINOX.date(2026), null, Options.defaults());
		Cell hidden = r.cells().stream().filter(c -> c.x() == 187.5 && c.y() == 37.5).findFirst().orElseThrow();
		assertThat(hidden.directMinutes()).isZero();
		assertThat(r.cells().stream().filter(c -> c.y() > 150 && c.directMinutes() > 0)).isNotEmpty();
	}

	@Test
	void 밝기_단계_기준() {
		assertThat(LightLevel.of(500, 0)).isEqualTo(LightLevel.DIM);
		assertThat(LightLevel.of(900, 0)).isEqualTo(LightLevel.MEDIUM);
		assertThat(LightLevel.of(500, 45)).isEqualTo(LightLevel.MEDIUM);
		assertThat(LightLevel.of(1600, 0)).isEqualTo(LightLevel.BRIGHT);
		assertThat(LightLevel.of(100, 150)).isEqualTo(LightLevel.BRIGHT);
	}
}
