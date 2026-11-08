package com.springboot.calculator;

import java.util.ArrayList;
import java.util.List;

/** building.footprint_wkt (MULTIPOLYGON) 읽기. 결과: 링 목록(바깥 링·구멍 구분 없이), 링 = {x, y} 점 목록. */
public final class Wkt {

	private Wkt() {
	}

	public static List<List<double[]>> rings(String wkt) {
		List<List<double[]>> rings = new ArrayList<>();
		if (wkt == null || wkt.contains("EMPTY")) {
			return rings;
		}
		int i = 0;
		int n = wkt.length();
		while (i < n) {
			// 가장 안쪽 괄호 "(x y, x y, ...)" 를 찾는다
			int open = wkt.indexOf('(', i);
			if (open < 0) {
				break;
			}
			int close = wkt.indexOf(')', open);
			int nextOpen = wkt.indexOf('(', open + 1);
			if (nextOpen >= 0 && nextOpen < close) {
				i = nextOpen;
				continue;
			}
			List<double[]> ring = new ArrayList<>();
			for (String pair : wkt.substring(open + 1, close).split(",")) {
				String[] xy = pair.trim().split("\\s+");
				if (xy.length >= 2) {
					ring.add(new double[] { Double.parseDouble(xy[0]), Double.parseDouble(xy[1]) });
				}
			}
			if (!ring.isEmpty()) {
				rings.add(ring);
			}
			i = close + 1;
		}
		return rings;
	}
}
