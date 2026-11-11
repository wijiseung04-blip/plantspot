package com.springboot.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.springboot.calculator.TerrainModel;
import com.springboot.calculator.TmConverter;
import com.springboot.calculator.TmConverter.LatLon;
import com.springboot.client.ElevationApi;
import com.springboot.domain.TerrainGrid;
import com.springboot.domain.TerrainPoint;
import com.springboot.repository.BuildingRepository;
import com.springboot.repository.TerrainGridRepository;
import com.springboot.repository.TerrainPointRepository;

/**
 * 지형(땅 높이) 격자 가져오기와 조회. [① 공개 데이터 → DB 전환]
 * 건물 데이터 범위 + 여유(기본 5km)를 격자 간격(기본 90m = 자료 해상도)으로 나눠 점마다 고도 API로 높이를 받아 저장한다.
 * 중간에 멈춰도(사용량 제한 등) 다시 실행하면 받은 점은 건너뛰고 이어서 받는다.
 */
@Service
public class TerrainService {

	private static final Logger log = LoggerFactory.getLogger(TerrainService.class);
	private static final int MAX_POINTS = 200_000;

	private final TerrainGridRepository grids;
	private final TerrainPointRepository points;
	private final BuildingRepository buildings;
	private final ElevationApi api;
	private final double spacing;
	private final double buffer;
	private final long delayMs;

	/** 읽어 둔 격자 (가져오기가 끝나면 비운다) */
	private volatile Loaded loaded;

	private record Loaded(Long gridId, TerrainModel model) {
	}

	public TerrainService(TerrainGridRepository grids, TerrainPointRepository points, BuildingRepository buildings,
			ElevationApi api, @Value("${plantspot.terrain.spacing:90}") double spacing,
			@Value("${plantspot.terrain.buffer:5000}") double buffer,
			@Value("${plantspot.terrain.delay-ms:300}") long delayMs) {
		this.grids = grids;
		this.points = points;
		this.buildings = buildings;
		this.api = api;
		this.spacing = spacing;
		this.buffer = buffer;
		this.delayMs = delayMs;
	}

	/** 지형 자료가 있으면 격자, 없으면 null. */
	public TerrainModel model() {
		Loaded l = loaded;
		if (l != null) {
			return l.model();
		}
		TerrainGrid g = grids.findTopByOrderByIdDesc().orElse(null);
		if (g == null) {
			return null;
		}
		TerrainModel m = new TerrainModel(g.getOriginX(), g.getOriginY(), g.getSpacing(), g.getNx(), g.getNy());
		List<Object[]> rows = points.findValues(g.getId());
		if (rows.isEmpty()) {
			return null;
		}
		for (Object[] r : rows) {
			m.set(((Number) r[0]).intValue(), ((Number) r[1]).intValue(), ((Number) r[2]).doubleValue());
		}
		loaded = new Loaded(g.getId(), m);
		log.info("지형 격자 {}점 읽음 ({}m 간격)", rows.size(), g.getSpacing());
		return m;
	}

	/** 땅 높이(해발 m). 지형 자료가 없거나 범위 밖이면 NaN. */
	public double groundAt(double x, double y) {
		TerrainModel m = model();
		return m == null ? Double.NaN : m.elevationAt(x, y);
	}

	public record Progress(int done, int total) {
	}

	public record ImportResult(int total, int fetched, int skipped, double minElevation, double maxElevation) {

		public String summary() {
			return String.format("지형 격자 %d점 중 새로 %d점 받음, 이미 있던 %d점 건너뜀 (높이 %.0f~%.0fm)", total, fetched,
					skipped, minElevation, maxElevation);
		}
	}

	/** 건물 데이터 범위 + 여유를 덮는 격자를 받아 저장한다. */
	public ImportResult importAll(Consumer<Progress> progress) {
		List<Object[]> ext = buildings.findExtent();
		if (ext.isEmpty() || ext.get(0)[0] == null) {
			throw new IllegalStateException("건물 데이터가 없습니다. 먼저 importBuildings 로 건물을 가져오세요.");
		}
		Object[] e = ext.get(0);
		double minX = Math.floor((((Number) e[0]).doubleValue() - buffer) / spacing) * spacing;
		double maxX = ((Number) e[1]).doubleValue() + buffer;
		double minY = Math.floor((((Number) e[2]).doubleValue() - buffer) / spacing) * spacing;
		double maxY = ((Number) e[3]).doubleValue() + buffer;
		int nx = (int) Math.ceil((maxX - minX) / spacing) + 1;
		int ny = (int) Math.ceil((maxY - minY) / spacing) + 1;
		int total = nx * ny;
		if (total > MAX_POINTS) {
			throw new IllegalStateException("격자 점이 너무 많습니다 (" + total + "점). plantspot.terrain.spacing 을 늘리세요.");
		}

		TerrainGrid grid = grids.findTopByOrderByIdDesc().orElse(null);
		if (grid == null || !grid.sameShape(minX, minY, spacing, nx, ny)) {
			if (grid != null) {
				log.info("건물 범위가 바뀌어 지형 격자를 새로 만듭니다");
				for (TerrainGrid old : grids.findAll()) {
					points.deleteAll(points.findByGridId(old.getId()));
					grids.delete(old);
				}
			}
			grid = grids.save(new TerrainGrid(minX, minY, spacing, nx, ny, api.sourceName()));
		}
		loaded = null;

		Set<Long> have = new HashSet<>();
		for (Object[] r : points.findValues(grid.getId())) {
			have.add(key(((Number) r[0]).intValue(), ((Number) r[1]).intValue()));
		}
		int skipped = have.size();
		log.info("지형 격자 {}×{} = {}점 ({}m 간격), 이미 {}점 있음", nx, ny, total, spacing, skipped);

		List<int[]> todo = new ArrayList<>();
		for (int iy = 0; iy < ny; iy++) {
			for (int ix = 0; ix < nx; ix++) {
				if (!have.contains(key(ix, iy))) {
					todo.add(new int[] { ix, iy });
				}
			}
		}
		int fetched = 0;
		for (int from = 0; from < todo.size(); from += api.maxBatch()) {
			List<int[]> batch = todo.subList(from, Math.min(todo.size(), from + api.maxBatch()));
			List<LatLon> ll = new ArrayList<>();
			for (int[] p : batch) {
				ll.add(TmConverter.toLatLon(minX + p[0] * spacing, minY + p[1] * spacing));
			}
			double[] z = api.elevations(ll);
			List<TerrainPoint> rows = new ArrayList<>();
			for (int i = 0; i < batch.size(); i++) {
				rows.add(new TerrainPoint(grid, batch.get(i)[0], batch.get(i)[1], z[i]));
			}
			points.saveAll(rows);
			fetched += batch.size();
			progress.accept(new Progress(skipped + fetched, total));
			if (delayMs > 0 && from + api.maxBatch() < todo.size()) {
				sleep(delayMs);
			}
		}
		grid.complete();
		grids.save(grid);
		loaded = null;

		TerrainModel m = model();
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (Object[] r : points.findValues(grid.getId())) {
			double v = ((Number) r[2]).doubleValue();
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		log.info("지형 가져오기 끝: {}", m != null ? "사용 가능" : "자료 없음");
		return new ImportResult(total, fetched, skipped, lo, hi);
	}

	/** 지형 자료 상태 (관리자 화면). */
	public record Status(boolean available, long points, int total, double spacing, String source, String completedAt) {
	}

	public Status status() {
		TerrainGrid g = grids.findTopByOrderByIdDesc().orElse(null);
		if (g == null) {
			return new Status(false, 0, 0, spacing, null, null);
		}
		long n = points.countByGridId(g.getId());
		return new Status(n > 0, n, g.getNx() * g.getNy(), g.getSpacing(), g.getSource(),
				g.getCompletedAt() != null ? g.getCompletedAt().toString() : null);
	}

	private static long key(int ix, int iy) {
		return ((long) ix << 32) | (iy & 0xffffffffL);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
