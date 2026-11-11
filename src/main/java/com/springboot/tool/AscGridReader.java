package com.springboot.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ESRI ASCII Grid(.asc) 읽기 — QGIS 「다른 이름으로 저장 → Arc/Info ASCII Grid」 형식.
 * <pre>
 * ncols 583
 * nrows 867
 * xllcorner 370000      (또는 xllcenter)
 * yllcorner 270000      (또는 yllcenter)
 * cellsize 30
 * NODATA_value -9999    (없어도 됨)
 * 값들 (맨 위 줄 = 북쪽 끝 행, 왼쪽 = 서쪽)
 * </pre>
 * 파일이 클 수 있어 값은 한 행씩 읽어 넘긴다.
 */
public final class AscGridReader {

	private AscGridReader() {
	}

	/** 머리말. x(col), y(row) 는 칸 가운데 좌표. */
	public record Header(int ncols, int nrows, double xll, double yll, double cellsize, boolean center,
			Double nodata) {

		public double x(int col) {
			return xll + (col + (center ? 0 : 0.5)) * cellsize;
		}

		/** row 0 = 맨 위(북쪽) */
		public double y(int row) {
			return yll + (nrows - 1 - row + (center ? 0 : 0.5)) * cellsize;
		}

		public boolean isNoData(double v) {
			return nodata != null && Math.abs(v - nodata) < 1e-6;
		}
	}

	public interface RowHandler {
		/** 한 행(row 0 = 북쪽 끝)을 받는다. 계속 읽으려면 true. */
		boolean row(int row, double[] values);
	}

	public static Header header(Path file) throws IOException {
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
			return readHeader(in).header();
		}
	}

	public static Header read(Path file, RowHandler handler) throws IOException {
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
			Parsed p = readHeader(in);
			Header h = p.header();
			double[] row = new double[h.ncols()];
			int r = 0;
			int c = 0;
			String line = p.firstValues();
			while (r < h.nrows()) {
				if (line == null) {
					line = in.readLine();
					if (line == null) {
						throw new IOException("값이 모자랍니다 (" + r + "행까지 읽음, 기대 " + h.nrows() + "행)");
					}
				}
				for (String tok : line.trim().split("\\s+")) {
					if (tok.isEmpty()) {
						continue;
					}
					row[c++] = Double.parseDouble(tok);
					if (c == h.ncols()) {
						if (!handler.row(r, row)) {
							return h;
						}
						r++;
						c = 0;
						if (r == h.nrows()) {
							break;
						}
					}
				}
				line = null;
			}
			return h;
		}
	}

	/** 머리말과, 머리말을 읽다가 먼저 읽게 된 첫 값 줄 */
	private record Parsed(Header header, String firstValues) {
	}

	private static Parsed readHeader(BufferedReader in) throws IOException {
		Integer ncols = null;
		Integer nrows = null;
		Double xll = null;
		Double yll = null;
		Double cell = null;
		Double nodata = null;
		boolean center = false;
		String first = null;
		String line;
		while ((line = in.readLine()) != null) {
			String t = line.trim();
			if (t.isEmpty()) {
				continue;
			}
			String[] kv = t.split("\\s+");
			String key = kv[0].toLowerCase(Locale.ROOT);
			if (!Character.isLetter(key.charAt(0))) {
				first = line; // 값 시작
				break;
			}
			if (kv.length < 2) {
				throw new IOException("Arc/Info ASCII Grid 형식이 아닙니다 (머리말: " + t.substring(0, Math.min(30, t.length())) + ")");
			}
			double v = Double.parseDouble(kv[1]);
			switch (key) {
			case "ncols" -> ncols = (int) v;
			case "nrows" -> nrows = (int) v;
			case "xllcorner" -> xll = v;
			case "yllcorner" -> yll = v;
			case "xllcenter" -> {
				xll = v;
				center = true;
			}
			case "yllcenter" -> yll = v;
			case "cellsize" -> cell = v;
			case "nodata_value" -> nodata = v;
			case "dx", "dy" -> throw new IOException(
					"가로·세로 칸 크기가 다릅니다(dx/dy). QGIS 「워프(재투영)」에서 해상도를 30 같은 한 값으로 정하세요.");
			default -> {
				// 모르는 머리말은 무시
			}
			}
		}
		if (ncols == null || nrows == null || xll == null || yll == null || cell == null) {
			throw new IOException("ASCII Grid 머리말(ncols, nrows, xllcorner, yllcorner, cellsize)이 없습니다. "
					+ "QGIS에서 형식을 「Arc/Info ASCII Grid」로 저장하세요.");
		}
		return new Parsed(new Header(ncols, nrows, xll, yll, cell, center, nodata), first);
	}
}
