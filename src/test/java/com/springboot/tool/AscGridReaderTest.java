package com.springboot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AscGridReaderTest {

	@TempDir
	Path dir;

	@Test
	void 머리말과_값_읽기() throws Exception {
		// 값이 줄바꿈과 상관없이 이어져도 읽는다
		Path f = dir.resolve("a.asc");
		Files.writeString(f, "NCOLS 3\nNROWS 2\nXLLCORNER 100\nYLLCORNER 200\nCELLSIZE 10\nNODATA_VALUE -9999\n"
				+ "1 2\n3\n4 5 -9999\n");
		List<double[]> rows = new ArrayList<>();
		AscGridReader.Header h = AscGridReader.read(f, (r, v) -> rows.add(v.clone()) || true);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsExactly(1, 2, 3);
		assertThat(rows.get(1)).containsExactly(4, 5, -9999);
		assertThat(h.isNoData(-9999)).isTrue();
		// 칸 가운데: 맨 위 행(row 0) = 북쪽
		assertThat(h.x(0)).isEqualTo(105);
		assertThat(h.y(0)).isEqualTo(215);
		assertThat(h.y(1)).isEqualTo(205);
	}

	@Test
	void center_머리말() throws Exception {
		Path f = dir.resolve("c.asc");
		Files.writeString(f, "ncols 1\nnrows 2\nxllcenter 100\nyllcenter 200\ncellsize 10\n7\n8\n");
		AscGridReader.Header h = AscGridReader.header(f);
		assertThat(h.x(0)).isEqualTo(100);
		assertThat(h.y(0)).isEqualTo(210);
		assertThat(h.nodata()).isNull();
	}

	@Test
	void 형식이_다르면_알려준다() throws Exception {
		Path tif = dir.resolve("x.asc");
		Files.writeString(tif, "II*\0garbage");
		assertThatThrownBy(() -> AscGridReader.header(tif)).hasMessageContaining("ASCII Grid");
		Path dxdy = dir.resolve("d.asc");
		Files.writeString(dxdy, "ncols 1\nnrows 1\nxllcorner 0\nyllcorner 0\ndx 30\ndy 31\n1\n");
		assertThatThrownBy(() -> AscGridReader.header(dxdy)).hasMessageContaining("dx/dy");
	}
}
