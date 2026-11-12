package com.springboot.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.springboot.domain.PlantSpecies;
import com.springboot.domain.LightLevel;
import com.springboot.domain.SeasonDay;
import com.springboot.calculator.SunlightCalculator.Cell;

class PlantScorerTest {

	static PlantSpecies plant(int minLux, int maxLux, int minDirect, Integer maxDirect, Integer minDist, Integer maxDist,
			Integer winter) {
		PlantSpecies p = PlantSpecies.manual("테스트");
		p.adjust("테스트", new PlantConverter.Converted(minLux, maxLux, minDirect, maxDirect, minDist, maxDist, winter));
		return p;
	}

	static Cell cell(double lux, int direct, double distance) {
		return new Cell(0, 0, 0, distance, direct, lux, 0, distance, LightLevel.of(lux, direct));
	}

	@Test
	void 범위_가운데면_100점_가운데서_멀어지면_곡선으로_감점() {
		PlantSpecies mid = plant(800, 1500, 0, 180, null, null, null);
		assertThat(PlantScorer.score(mid, cell(1095, 60, 100))).isEqualTo(100.0); // 로그 눈금 가운데
		// 예전에는 가운데 절반(약 935~1280 lux)이 모두 100점이었다
		assertThat(PlantScorer.score(mid, cell(1000, 60, 100))).isEqualTo(98.3);
		assertThat(PlantScorer.score(mid, cell(950, 60, 100))).isEqualTo(95.9);
		assertThat(PlantScorer.score(mid, cell(1250, 60, 100))).isEqualTo(96.5);
	}

	@Test
	void 범위가_비슷한_식물도_점수가_갈린다() {
		PlantSpecies a = plant(800, 1500, 0, 180, null, null, null);
		PlantSpecies b = plant(800, 1800, 0, 180, null, null, null);
		Cell c = cell(1000, 0, 100);
		assertThat(PlantScorer.score(a, c)).isNotEqualTo(PlantScorer.score(b, c));
	}

	@Test
	void 항목별_점수를_곱하면_칸_점수() {
		PlantSpecies p = plant(300, 10000, 0, null, 0, 50, null);
		PlantScorer.Parts parts = PlantScorer.parts(p, cell(1700, 0, 125));
		assertThat(parts.distance()).isEqualTo(0.85);
		assertThat(parts.score()).isEqualTo(Math.round(1000 * parts.light() * parts.direct() * parts.distance()) / 10.0);
	}

	@Test
	void 점수_내역은_종합_점수_계산과_같다() {
		PlantSpecies p = plant(800, 1500, 0, 180, null, null, 13);
		List<Cell> cells = List.of(cell(1000, 0, 100), cell(200, 0, 300));
		var r = PlantScorer.evaluate(p, seasons(cells, cells, cells, cells), 8.0);
		var b = r.breakdown();
		assertThat(b.seasons()).hasSize(4);
		assertThat(b.seasons().get(0).parts().score()).isEqualTo(98.3);
		assertThat(b.spotScore()).isEqualTo(98.3);
		assertThat(b.roomyBonus()).isEqualTo(5.0); // 2칸 중 1칸
		assertThat(b.temperatureFactor()).isEqualTo(0.7);
		assertThat(b.total()).isEqualTo(r.score());
		assertThat(b.total()).isCloseTo((b.spotScore() * 0.9 + b.roomyBonus()) * b.temperatureFactor(), org.assertj.core.data.Offset.offset(0.2));
	}

	@Test
	void 빛이_모자라거나_직사광이_넘치면_감점() {
		PlantSpecies shade = plant(300, 800, 0, 60, null, null, null);
		assertThat(PlantScorer.score(shade, cell(150, 0, 300))).isLessThan(70);
		assertThat(PlantScorer.score(shade, cell(500, 240, 50))).isLessThan(50);
		PlantSpecies sun = plant(1500, 10000, 120, null, null, null, null);
		assertThat(PlantScorer.score(sun, cell(2000, 0, 50))).isLessThan(40);
		assertThat(PlantScorer.score(sun, cell(4000, 180, 50))).isEqualTo(100.0);
	}

	@Test
	void 범위_끝으로_갈수록_조금씩_감점() {
		PlantSpecies mid = plant(800, 1500, 0, 180, null, null, null);
		assertThat(PlantScorer.score(mid, cell(1095, 0, 100))).isEqualTo(100.0); // 가운데
		assertThat(PlantScorer.score(mid, cell(800, 0, 100))).isEqualTo(80.0); // 범위 끝
		assertThat(PlantScorer.score(mid, cell(850, 0, 100))).isBetween(80.0, 100.0);
		assertThat(PlantScorer.score(mid, cell(700, 0, 100))).isLessThan(80); // 범위 밖은 더 낮다
	}

	@Test
	void 농사로_배치_거리에서_벗어난_만큼_감점() {
		PlantSpecies p = plant(300, 10000, 0, null, 0, 50, null);
		assertThat(PlantScorer.score(p, cell(1700, 0, 30))).isEqualTo(100.0);
		assertThat(PlantScorer.score(p, cell(1700, 0, 125))).isEqualTo(85.0); // 75cm 벗어남
		assertThat(PlantScorer.score(p, cell(1700, 0, 400))).isEqualTo(70.0); // 150cm 이상
	}

	@Test
	void 잘_맞는_자리가_많은_방이_점수가_높다() {
		PlantSpecies p = plant(800, 1500, 0, 180, null, null, null);
		List<Cell> few = List.of(cell(1100, 0, 100), cell(200, 0, 300), cell(200, 0, 400), cell(200, 0, 500));
		List<Cell> many = List.of(cell(1100, 0, 100), cell(1100, 0, 150), cell(1100, 0, 200), cell(200, 0, 500));
		PlantScorer.PlantResult a = PlantScorer.evaluate(p, seasons(few, few, few, few), null);
		PlantScorer.PlantResult b = PlantScorer.evaluate(p, seasons(many, many, many, many), null);
		assertThat(a.score()).isEqualTo(92.5); // 100 × 0.9 + 25% × 10
		assertThat(b.score()).isEqualTo(97.5);
		assertThat(b.roomyShare()).isEqualTo(0.75);
		assertThat(b.notes()).noneMatch(n -> n.contains("%")); // 비율은 주의 사항이 아니라 따로 보여 준다
	}

	private static Map<SeasonDay, List<Cell>> seasons(List<Cell> spring, List<Cell> summer, List<Cell> autumn,
			List<Cell> winter) {
		Map<SeasonDay, List<Cell>> m = new EnumMap<>(SeasonDay.class);
		m.put(SeasonDay.SPRING_EQUINOX, spring);
		m.put(SeasonDay.SUMMER_SOLSTICE, summer);
		m.put(SeasonDay.AUTUMN_EQUINOX, autumn);
		m.put(SeasonDay.WINTER_SOLSTICE, winter);
		return m;
	}

	@Test
	void 계절_내내_맞는_칸이_있으면_고정_자리() {
		List<Cell> same = List.of(cell(1000, 0, 100), cell(200, 0, 300));
		var r = PlantScorer.evaluate(plant(800, 1500, 0, 180, null, null, null), seasons(same, same, same, same), null);
		assertThat(r.type()).isEqualTo(PlantScorer.SpotType.FIXED);
		assertThat(r.fixed().cell().lux()).isEqualTo(1000);
	}

	@Test
	void 계절마다_좋은_칸이_다르면_계절_이동() {
		// 칸 0은 여름에만, 칸 1은 겨울에만 밝다
		List<Cell> summer = List.of(cell(1000, 0, 100), cell(100, 0, 300));
		List<Cell> winter = List.of(cell(100, 0, 100), cell(1000, 0, 300));
		var r = PlantScorer.evaluate(plant(800, 1500, 0, 180, null, null, null), seasons(summer, summer, winter, winter), null);
		assertThat(r.type()).isEqualTo(PlantScorer.SpotType.SEASONAL);
		assertThat(r.seasonal().get(SeasonDay.SUMMER_SOLSTICE).cell().distanceToWindow()).isEqualTo(100);
		assertThat(r.seasonal().get(SeasonDay.WINTER_SOLSTICE).cell().distanceToWindow()).isEqualTo(300);
	}

	@Test
	void 겨울_실내_온도가_낮으면_경고와_감점() {
		List<Cell> same = List.of(cell(1000, 0, 100));
		PlantSpecies p = plant(800, 1500, 0, 180, null, null, 13);
		var warm = PlantScorer.evaluate(p, seasons(same, same, same, same), 18.0);
		var cold = PlantScorer.evaluate(p, seasons(same, same, same, same), 8.0);
		assertThat(cold.score()).isLessThan(warm.score());
		assertThat(cold.notes()).anyMatch(n -> n.contains("13℃"));
	}

	@Test
	void 계절_구분() {
		assertThat(Seasons.of(java.time.LocalDate.of(2026, 11, 8))).isEqualTo(SeasonDay.AUTUMN_EQUINOX);
		assertThat(Seasons.of(java.time.LocalDate.of(2027, 1, 8))).isEqualTo(SeasonDay.WINTER_SOLSTICE);
		assertThat(Seasons.next(SeasonDay.WINTER_SOLSTICE)).isEqualTo(SeasonDay.SPRING_EQUINOX);
	}
}
