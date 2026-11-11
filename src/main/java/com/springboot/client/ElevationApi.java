package com.springboot.client;

import java.util.List;

import com.springboot.calculator.TmConverter.LatLon;

/** 위경도 점들의 땅 높이(해발 m)를 돌려주는 고도 API. */
public interface ElevationApi {

	/** 한 번에 보낼 수 있는 최대 점 수 */
	int maxBatch();

	/** 자료 이름 (terrain_grid.source 에 남김) */
	String sourceName();

	/** points 와 같은 순서의 높이. 바다 등 값이 없으면 0. */
	double[] elevations(List<LatLon> points);
}
