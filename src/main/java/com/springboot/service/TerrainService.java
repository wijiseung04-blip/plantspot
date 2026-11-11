package com.springboot.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.calculator.TerrainModel;
import com.springboot.domain.TerrainGrid;
import com.springboot.repository.BuildingRepository;
import com.springboot.repository.TerrainGridRepository;
import com.springboot.repository.TerrainPointRepository;
import com.springboot.tool.AscGridReader;

/**
 * 지형(땅 높이) 격자 가져오기와 조회. [① 공개 데이터 → DB 전환: 수치표고모델(DEM) 파일 → terrain_grid·terrain_point]
 * QGIS 에서 EPSG:5186 으로 바꿔 ASCII Grid(.asc)로 저장한 파일을 읽어, 건물 데이터 범위 + 여유(기본 5km)만 잘라 저장한다.
 * 점이 너무 많으면(기본 150만 점 초과) 몇 칸씩 건너뛰어 줄인다.
 */
@Service
public class TerrainService {

	private static final Logger log = LoggerFactory.getLogger(TerrainService.class);
	private static final int BATCH = 5000;

	private final TerrainGridRepository grids;
	private final TerrainPointRepository points;
	private final BuildingRepository buildings;
	private final JdbcTemplate jdbc;
	private final double buffer;
	private final int maxPoints;

	/** 읽어 둔 격자 (가져오기가 끝나면 비운다) */
	private volatile TerrainModel loaded;

	public TerrainService(TerrainGridRepository grids, TerrainPointRepository points, BuildingRepository buildings,
			JdbcTemplate jdbc, @Value("${plantspot.terrain.buffer:5000}") double buffer,
			@Value("${plantspot.terrain.max-points:1500000}") int maxPoints) {
		this.grids = grids;
		this.points = points;
		this.buildings = buildings;
		this.jdbc = jdbc;
		this.buffer = buffer;
		this.maxPoints = maxPoints;
	}

	/** 지형 자료가 있으면 격자, 없으면 null. */
	public TerrainModel model() {
		TerrainModel m = loaded;
		if (m != null) {
			return m;
		}
		TerrainGrid g = grids.findTopByOrderByIdDesc().orElse(null);
		if (g == null) {
			return null;
		}
		List<Object[]> rows = points.findValues(g.getId());
		if (rows.isEmpty()) {
			return null;
		}
		m = new TerrainModel(g.getOriginX(), g.getOriginY(), g.getSpacing(), g.getNx(), g.getNy());
		for (Object[] r : rows) {
			m.set(((Number) r[0]).intValue(), ((Number) r[1]).intValue(), ((Number) r[2]).doubleValue());
		}
		loaded = m;
		log.info("지형 격자 {}점 읽음 ({}m 간격)", rows.size(), g.getSpacing());
		return m;
	}

	/** 땅 높이(해발 m). 지형 자료가 없거나 범위 밖이면 NaN. */
	public double groundAt(double x, double y) {
		TerrainModel m = model();
		return m == null ? Double.NaN : m.elevationAt(x, y);
	}

	public record ImportResult(String file, int points, int nx, int ny, double spacing, int step, double minElevation,
			double maxElevation) {

		public String summary() {
			return String.format("지형 %,d점 저장 (%d×%d, %.0fm 간격%s) · 높이 %.0f~%.0fm · %s", points, nx, ny, spacing,
					step > 1 ? ", 원본 " + step + "칸마다 1칸" : "", minElevation, maxElevation, file);
		}
	}

	/** DEM ASCII Grid 파일을 읽어 이전 지형 자료를 바꾼다. */
	@Transactional
	public ImportResult importFile(Path file, Consumer<String> progress) {
		if (!Files.isRegularFile(file)) {
			throw new IllegalArgumentException("DEM 파일이 없습니다: " + file.toAbsolutePath());
		}
		List<Object[]> ext = buildings.findExtent();
		if (ext.isEmpty() || ext.get(0)[0] == null) {
			throw new IllegalStateException("건물 데이터가 없습니다. 먼저 importBuildings 로 건물을 가져오세요.");
		}
		Object[] e = ext.get(0);
		double minX = ((Number) e[0]).doubleValue() - buffer;
		double maxX = ((Number) e[1]).doubleValue() + buffer;
		double minY = ((Number) e[2]).doubleValue() - buffer;
		double maxY = ((Number) e[3]).doubleValue() + buffer;
		try {
			AscGridReader.Header h = AscGridReader.header(file);
			// 건물 범위 + 여유에 드는 열·행
			int c0 = Math.max(0, (int) Math.floor((minX - h.x(0)) / h.cellsize()));
			int c1 = Math.min(h.ncols() - 1, (int) Math.ceil((maxX - h.x(0)) / h.cellsize()));
			int rTop = Math.max(0, (int) Math.floor((h.y(0) - maxY) / h.cellsize()));
			int rBottom = Math.min(h.nrows() - 1, (int) Math.ceil((h.y(0) - minY) / h.cellsize()));
			if (c0 > c1 || rTop > rBottom) {
				throw new IllegalStateException(String.format(
						"DEM 파일 범위(x %.0f~%.0f, y %.0f~%.0f)가 건물 데이터 범위와 겹치지 않습니다. "
								+ "QGIS 에서 좌표계를 EPSG:5186 으로 바꿔 저장했는지 확인하세요.",
						h.x(0), h.x(h.ncols() - 1), h.y(h.nrows() - 1), h.y(0)));
			}
			long cells = (long) (c1 - c0 + 1) * (rBottom - rTop + 1);
			int step = (int) Math.max(1, Math.ceil(Math.sqrt((double) cells / maxPoints)));
			int nx = (c1 - c0) / step + 1;
			int ny = (rBottom - rTop) / step + 1;
			double spacing = h.cellsize() * step;
			int bottomRow = rTop + (ny - 1) * step;
			progress.accept(String.format("원본 %d×%d (%.1fm) 중 %d×%d 를 %.0fm 간격으로 저장", h.ncols(), h.nrows(),
					h.cellsize(), nx, ny, spacing));

			// 이전 자료 지우기 (점은 엔티티로 읽지 않으므로 한 번에 지워도 된다)
			jdbc.update("delete from terrain_point");
			grids.deleteAll(grids.findAll());
			grids.flush();
			TerrainGrid grid = grids.saveAndFlush(new TerrainGrid(h.x(c0), h.y(bottomRow), spacing, nx, ny,
					file.getFileName().toString()));
			loaded = null;

			List<Object[]> batch = new ArrayList<>(BATCH);
			double[] stats = { Double.MAX_VALUE, -Double.MAX_VALUE, 0 };
			AscGridReader.read(file, (row, values) -> {
				if (row > bottomRow) {
					return false;
				}
				if (row < rTop || (row - rTop) % step != 0) {
					return true;
				}
				int iy = (bottomRow - row) / step;
				for (int c = c0, ix = 0; ix < nx; c += step, ix++) {
					double v = values[c];
					if (h.isNoData(v)) {
						continue;
					}
					batch.add(new Object[] { grid.getId(), ix, iy, v });
					stats[0] = Math.min(stats[0], v);
					stats[1] = Math.max(stats[1], v);
					stats[2]++;
					if (batch.size() == BATCH) {
						flush(batch);
					}
				}
				if (iy % 100 == 0) {
					progress.accept(String.format("  %,d점", (long) stats[2]));
				}
				return true;
			});
			flush(batch);
			grid.complete();
			grids.save(grid);
			loaded = null;
			if (stats[2] == 0) {
				throw new IllegalStateException("DEM 파일에서 값이 있는 점을 하나도 찾지 못했습니다.");
			}
			log.info("지형 가져오기: {} → {}점", file.getFileName(), (long) stats[2]);
			return new ImportResult(file.getFileName().toString(), (int) stats[2], nx, ny, spacing, step, stats[0],
					stats[1]);
		} catch (IOException ex) {
			throw new UncheckedIOException("DEM 파일을 읽을 수 없습니다: " + ex.getMessage(), ex);
		}
	}

	private void flush(List<Object[]> batch) {
		if (!batch.isEmpty()) {
			jdbc.batchUpdate("insert into terrain_point (grid_id, ix, iy, elevation) values (?, ?, ?, ?)", batch);
			batch.clear();
		}
	}

	/** 지형 자료 상태 (관리자 화면). */
	public record Status(boolean available, long points, int total, double spacing, String source, String completedAt) {
	}

	public Status status() {
		TerrainGrid g = grids.findTopByOrderByIdDesc().orElse(null);
		if (g == null) {
			return new Status(false, 0, 0, 0, null, null);
		}
		long n = points.countByGridId(g.getId());
		return new Status(n > 0, n, g.getNx() * g.getNy(), g.getSpacing(), g.getSource(),
				g.getCompletedAt() != null ? g.getCompletedAt().toString() : null);
	}
}
