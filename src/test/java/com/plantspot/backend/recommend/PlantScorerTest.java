package com.plantspot.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.plantspot.backend.plant.PlantConverter;
import com.plantspot.backend.plant.PlantSpecies;
import com.plantspot.backend.sunlight.LightLevel;
import com.plantspot.backend.sunlight.SeasonDay;
import com.plantspot.backend.sunlight.SunlightCalculator.Cell;

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
	void 범위_안이면_100점() {
		assertThat(PlantScorer.score(plant(800, 1500, 0, 180, null, null, null), cell(1000, 60, 100))).isEqualTo(100.0);
	}

	@Test
	void 빛이_모자라거나_직사광이_넘치면_감점() {
		PlantSpecies shade = plant(300, 800, 0, 60, null, null, null);
		assertThat(PlantScorer.score(shade, cell(150, 0, 300))).isLessThan(70);
		assertThat(PlantScorer.score(shade, cell(500, 240, 50))).isLessThan(50);
		PlantSpecies sun = plant(1500, 10000, 120, null, null, null, null);
		assertThat(PlantScorer.score(sun, cell(2000, 0, 50))).isLessThan(40);
		assertThat(PlantScorer.score(sun, cell(2000, 180, 50))).isEqualTo(100.0);
	}

	@Test
	void 농사로_배치_거리_밖은_조금_감점() {
		PlantSpecies p = plant(300, 10000, 0, null, 0, 50, null);
		assertThat(PlantScorer.score(p, cell(1000, 0, 30))).isEqualTo(100.0);
		assertThat(PlantScorer.score(p, cell(1000, 0, 200))).isEqualTo(85.0);
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
