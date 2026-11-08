package com.plantspot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class GpkgGeometryReaderTest {

	@Test
	void 사각형_넓이와_중심점() {
		List<GpkgGeometryReader.Polygon> polygons = GpkgGeometryReader
				.read(GpkgTestData.rectangle(378721.47, 281651.93, 20, 15));
		assertThat(polygons).hasSize(1);
		GpkgGeometryReader.Shape s = GpkgGeometryReader.shape(polygons);
		assertThat(s.area()).isCloseTo(300.0, within(1e-6));
		assertThat(s.centerX()).isCloseTo(378731.47, within(1e-6));
		assertThat(s.centerY()).isCloseTo(281659.43, within(1e-6));
	}

	@Test
	void 빅엔디언_멀티폴리곤_구멍은_넓이에서_뺀다() {
		List<GpkgGeometryReader.Polygon> polygons = GpkgGeometryReader.read(GpkgTestData.multiPolygonWithHole());
		assertThat(polygons).hasSize(1);
		assertThat(polygons.get(0).rings()).hasSize(2);
		GpkgGeometryReader.Shape s = GpkgGeometryReader.shape(polygons);
		assertThat(s.area()).isCloseTo(96.0, within(1e-9));
		// (100×(5,5) − 4×(3,3)) / 96
		assertThat(s.centerX()).isCloseTo((500 - 12) / 96.0, within(1e-9));
		assertThat(s.centerY()).isCloseTo((500 - 12) / 96.0, within(1e-9));
	}

	@Test
	void WKT로_바꾸기() {
		String wkt = GpkgGeometryReader.toWkt(GpkgGeometryReader.read(GpkgTestData.rectangle(0, 0, 1, 2)));
		assertThat(wkt).isEqualTo("MULTIPOLYGON (((0.00 0.00, 1.00 0.00, 1.00 2.00, 0.00 2.00, 0.00 0.00)))");
	}

	@Test
	void GP_헤더가_아니면_오류() {
		assertThatThrownBy(() -> GpkgGeometryReader.read(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
