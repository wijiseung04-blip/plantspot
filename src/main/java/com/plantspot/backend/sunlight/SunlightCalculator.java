package com.plantspot.backend.sunlight;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.plantspot.backend.house.Point;
import com.plantspot.backend.obstruction.ObstructionProfile;
import com.plantspot.backend.sun.SolarCalculator;
import com.plantspot.backend.sun.SolarPosition;

/**
 * 방 바닥(또는 선반 높이)을 25cm 칸으로 나누고 하루 동안 칸별 햇빛을 계산한다. [① 분석 후 저장]
 *
 * <h3>직사광</h3>
 * 15분마다 해 위치를 구해, 해가 창 앞쪽(창 방향 ±90° 안)에 있고 그 방향 가림 각도보다 높으면
 * 창 네 모서리를 해 반대 방향으로 바닥에 투영한다. 투영된 사각형 안에 중심이 들어오는 칸에 15분을 더한다.
 * 칸에서 창까지 가는 빛이 방의 다른 벽(ㄱ자 방의 모서리 등)을 지나면 제외한다.
 *
 * <h3>밝기(하늘빛)</h3>
 * CIE 흐린 하늘 모델의 하늘 성분(창을 20cm 조각으로 나눠 적분, 가림 각도 아래는 건물 반사 20%)과
 * BRE 내부 반사 성분 공식으로 주광률(실외 대비 비율)을 구한 뒤, 실외 확산광 추정값을 곱해 평균 lux를 낸다.
 * 유리 투과율 0.75와 커튼 투과율을 곱한다. 절대값은 추정치이며 방 안 센서 비율로 보정한다(4단계).
 */
public final class SunlightCalculator {

	public static final double DEFAULT_CELL_CM = 25;
	public static final int DEFAULT_STEP_MIN = 15;
	private static final double GLASS_TRANSMITTANCE = 0.75;
	private static final double BUILDING_REFLECTANCE = 0.2;
	private static final double PATCH_CM = 20;
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SolarCalculator solar;

	public SunlightCalculator(SolarCalculator solar) {
		this.solar = solar;
	}

	// ----- 입력 -----

	/** 창 하나의 평면도 기하. start = 창 왼쪽 끝(벽 위), along = 벽 방향 단위 벡터, outward = 바깥 법선 단위 벡터. */
	public record WindowGeom(long id, int wallIndex, double startX, double startY, double alongX, double alongY,
			double outwardX, double outwardY, double width, double height, double sill, double azimuthDeg) {

		double endX() {
			return startX + alongX * width;
		}

		double endY() {
			return startY + alongY * width;
		}
	}

	public record RoomGeom(List<Point> polygon, double orientationDeg, double ceilingHeight, List<WindowGeom> windows,
			double curtainTransmittance) {
	}

	/** 방 다각형과 창 정보로 RoomGeom을 만든다. windows의 각 원소: {id, wallIndex, offset, width, height, sill, azimuth}. */
	public static RoomGeom room(List<Point> polygon, double orientationDeg, double ceilingHeight,
			List<double[]> windows, double curtainTransmittance) {
		double area = 0;
		for (int i = 0; i < polygon.size(); i++) {
			Point p = polygon.get(i);
			Point q = polygon.get((i + 1) % polygon.size());
			area += p.x() * q.y() - q.x() * p.y();
		}
		List<WindowGeom> geoms = new ArrayList<>();
		for (double[] w : windows) {
			int wall = (int) w[1];
			Point a = polygon.get(wall);
			Point b = polygon.get((wall + 1) % polygon.size());
			double len = Math.hypot(b.x() - a.x(), b.y() - a.y());
			double ux = (b.x() - a.x()) / len;
			double uy = (b.y() - a.y()) / len;
			double nx = area > 0 ? uy : -uy;
			double ny = area > 0 ? -ux : ux;
			geoms.add(new WindowGeom((long) w[0], wall, a.x() + ux * w[2], a.y() + uy * w[2], ux, uy, nx, ny, w[3],
					w[4], w[5], w[6]));
		}
		return new RoomGeom(polygon, orientationDeg, ceilingHeight, geoms, curtainTransmittance);
	}

	/** workHeight: 계산 높이(cm, 0 = 바닥, 화분 선반이면 그 높이). */
	public record Options(double cellSize, int stepMinutes, double workHeight) {

		public static Options defaults() {
			return new Options(DEFAULT_CELL_CM, DEFAULT_STEP_MIN, 0);
		}
	}

	// ----- 결과 -----

	public record Cell(int ix, int iy, double x, double y, int directMinutes, double lux, double daylightFactor,
			double distanceToWindow, LightLevel level) {
	}

	public record Result(LocalDate date, double cellSize, List<Cell> cells, int daylightMinutes, double outdoorLux,
			List<Integer> windowSunMinutes) {

		public int maxDirectMinutes() {
			return cells.stream().mapToInt(Cell::directMinutes).max().orElse(0);
		}
	}

	// ----- 계산 -----

	public Result calculate(RoomGeom room, double latitude, double longitude, LocalDate date,
			ObstructionProfile obstruction, Options opt) {
		List<double[]> centers = cellCenters(room.polygon(), opt.cellSize());
		int n = centers.size();
		int[] direct = new int[n];
		int[] windowSun = new int[room.windows().size()];
		int daylight = 0;

		for (int minute = 0; minute < 1440; minute += opt.stepMinutes()) {
			SolarPosition sun = solar.position(date.atStartOfDay().plusMinutes(minute).atZone(SEOUL).toInstant(),
					latitude, longitude);
			if (sun.altitudeDeg() <= 0) {
				continue;
			}
			daylight += opt.stepMinutes();
			boolean[] lit = new boolean[n];
			for (int w = 0; w < room.windows().size(); w++) {
				WindowGeom win = room.windows().get(w);
				double[][] quad = sunPatch(room, win, sun, obstruction, opt.workHeight());
				if (quad == null) {
					continue;
				}
				windowSun[w] += opt.stepMinutes();
				double[] toSun = towardSun(room.orientationDeg(), sun.azimuthDeg());
				for (int c = 0; c < n; c++) {
					if (!lit[c] && insidePolygon(centers.get(c)[0], centers.get(c)[1], quad)
							&& reachesWindow(room.polygon(), win, centers.get(c), toSun)) {
						lit[c] = true;
					}
				}
			}
			for (int c = 0; c < n; c++) {
				if (lit[c]) {
					direct[c] += opt.stepMinutes();
				}
			}
		}

		double outdoorLux = outdoorDiffuseLux(latitude, longitude, date);
		double irc = internallyReflected(room, obstruction);
		double transmit = GLASS_TRANSMITTANCE * room.curtainTransmittance();
		List<Cell> cells = new ArrayList<>(n);
		double minX = room.polygon().stream().mapToDouble(Point::x).min().orElse(0);
		double minY = room.polygon().stream().mapToDouble(Point::y).min().orElse(0);
		for (int c = 0; c < n; c++) {
			double[] p = centers.get(c);
			double df = (skyComponent(room, p, obstruction, opt.workHeight()) + irc) * transmit;
			double lux = df * outdoorLux;
			int ix = (int) Math.floor((p[0] - minX) / opt.cellSize());
			int iy = (int) Math.floor((p[1] - minY) / opt.cellSize());
			cells.add(new Cell(ix, iy, p[0], p[1], direct[c], Math.round(lux), round4(df),
					Math.round(distanceToWindows(room, p)), LightLevel.of(lux, direct[c])));
		}
		List<Integer> ws = new ArrayList<>();
		for (int v : windowSun) {
			ws.add(v);
		}
		return new Result(date, opt.cellSize(), cells, daylight, Math.round(outdoorLux), ws);
	}

	/** 해를 향하는 평면도 방향 단위 벡터 (평면도 위쪽 = orientationDeg). */
	static double[] towardSun(double orientationDeg, double sunAzimuth) {
		double r = Math.toRadians(sunAzimuth - orientationDeg);
		return new double[] { Math.sin(r), -Math.cos(r) };
	}

	/**
	 * 이 시각 창으로 들어온 직사광이 계산 높이에 닿는 사각형(평면도 좌표). 해가 창 뒤쪽이거나 가려지면 null.
	 */
	public static double[][] sunPatch(RoomGeom room, WindowGeom win, SolarPosition sun, ObstructionProfile obstruction,
			double workHeight) {
		if (sun.altitudeDeg() <= 0) {
			return null;
		}
		double rel = normalize180(sun.azimuthDeg() - win.azimuthDeg());
		if (Math.abs(rel) >= 90) {
			return null; // 해가 창 뒤쪽
		}
		if (obstruction != null && sun.altitudeDeg() <= obstruction.angleAt(sun.azimuthDeg())) {
			return null; // 앞 건물에 가림
		}
		double low = Math.max(win.sill(), workHeight);
		double high = win.sill() + win.height();
		if (high <= workHeight) {
			return null;
		}
		double[] u = towardSun(room.orientationDeg(), sun.azimuthDeg());
		double cot = 1 / Math.tan(Math.toRadians(sun.altitudeDeg()));
		double d1 = (low - workHeight) * cot;
		double d2 = (high - workHeight) * cot;
		double sx = win.startX(), sy = win.startY(), ex = win.endX(), ey = win.endY();
		return new double[][] {
				{ sx - u[0] * d1, sy - u[1] * d1 },
				{ ex - u[0] * d1, ey - u[1] * d1 },
				{ ex - u[0] * d2, ey - u[1] * d2 },
				{ sx - u[0] * d2, sy - u[1] * d2 } };
	}

	/** 칸에서 해 쪽으로 창 벽까지 가는 수평 경로가 다른 벽을 지나지 않는가. */
	private static boolean reachesWindow(List<Point> polygon, WindowGeom win, double[] c, double[] toSun) {
		// 창 벽 직선과 만나는 점
		double denom = toSun[0] * win.outwardX() + toSun[1] * win.outwardY();
		if (denom <= 1e-9) {
			return false;
		}
		double t = ((win.startX() - c[0]) * win.outwardX() + (win.startY() - c[1]) * win.outwardY()) / denom;
		double qx = c[0] + toSun[0] * t;
		double qy = c[1] + toSun[1] * t;
		int n = polygon.size();
		for (int i = 0; i < n; i++) {
			if (i == win.wallIndex()) {
				continue;
			}
			Point a = polygon.get(i);
			Point b = polygon.get((i + 1) % n);
			if (properIntersect(c[0], c[1], qx, qy, a.x(), a.y(), b.x(), b.y())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 하늘 성분(주광률 중 창으로 직접 보이는 하늘에서 오는 부분, 0~1).
	 * CIE 흐린 하늘: L(e) = Lz(1 + 2 sin e)/3, 수평면 조도 E = 7πLz/9.
	 */
	private static double skyComponent(RoomGeom room, double[] c, ObstructionProfile obstruction, double workHeight) {
		double sum = 0;
		for (WindowGeom w : room.windows()) {
			int nu = Math.max(1, (int) Math.ceil(w.width() / PATCH_CM));
			int nv = Math.max(1, (int) Math.ceil(w.height() / PATCH_CM));
			double du = w.width() / nu;
			double dv = w.height() / nv;
			for (int i = 0; i < nu; i++) {
				double px = w.startX() + w.alongX() * (i + 0.5) * du;
				double py = w.startY() + w.alongY() * (i + 0.5) * du;
				// 칸에서 창 조각까지 (수평)
				double hx = px - c[0];
				double hy = py - c[1];
				double horiz = Math.hypot(hx, hy);
				// 창 안쪽 법선과 이루는 각: 칸이 창 앞(방 안)에 있어야 한다
				double inward = -(hx * w.outwardX() + hy * w.outwardY());
				if (inward >= 0) {
					continue;
				}
				double azimuth = room.orientationDeg() + Math.toDegrees(Math.atan2(hx, -hy));
				for (int j = 0; j < nv; j++) {
					double z = w.sill() + (j + 0.5) * dv - workHeight;
					if (z <= 0) {
						continue;
					}
					double r2 = horiz * horiz + z * z;
					double r = Math.sqrt(r2);
					double elevation = Math.toDegrees(Math.atan2(z, horiz));
					double luminance = (1 + 2 * Math.sin(Math.toRadians(elevation))) / 3 * 9 / (7 * Math.PI);
					if (obstruction != null && elevation <= obstruction.angleAt(azimuth)) {
						luminance *= BUILDING_REFLECTANCE;
					}
					double cosWindow = -inward / r;
					double cosPlane = z / r;
					sum += luminance * du * dv * cosWindow * cosPlane / r2;
				}
			}
		}
		return sum;
	}

	/**
	 * 내부 반사 성분(BRE 간이식): IRC = 0.85W / (A(1−R)) × (C·Rfw + 5·Rcw) [%].
	 * W 창 면적, A 방 전체 표면적, R 평균 반사율 0.5, Rfw 바닥·아래 벽 0.3, Rcw 천장·위 벽 0.7,
	 * C는 창 앞 가림 각도에 따른 계수(0° 39 → 80° 5).
	 */
	private static double internallyReflected(RoomGeom room, ObstructionProfile obstruction) {
		if (room.windows().isEmpty()) {
			return 0;
		}
		double windowArea = 0;
		double weightedC = 0;
		for (WindowGeom w : room.windows()) {
			double a = w.width() * w.height();
			windowArea += a;
			double obs = obstruction == null ? 0 : obstruction.averageAngle(w.azimuthDeg(), 45);
			weightedC += a * coefficientC(obs);
		}
		double c = weightedC / windowArea;
		double floor = Math.abs(polygonArea(room.polygon()));
		double perimeter = 0;
		for (int i = 0; i < room.polygon().size(); i++) {
			Point p = room.polygon().get(i);
			Point q = room.polygon().get((i + 1) % room.polygon().size());
			perimeter += Math.hypot(q.x() - p.x(), q.y() - p.y());
		}
		double total = 2 * floor + perimeter * room.ceilingHeight();
		double percent = 0.85 * windowArea / (total * (1 - 0.5)) * (c * 0.3 + 5 * 0.7);
		return percent / 100;
	}

	private static double coefficientC(double obstructionDeg) {
		double[] table = { 39, 35, 31, 25, 20, 14, 10, 7, 5 };
		double idx = Math.max(0, Math.min(8, obstructionDeg / 10));
		int lo = (int) Math.floor(idx);
		int hi = Math.min(8, lo + 1);
		return table[lo] + (table[hi] - table[lo]) * (idx - lo);
	}

	/**
	 * 낮 시간 평균 실외 확산광(lux) 추정: 5,000 + 10,000 × sin(정오 해 고도).
	 * 부산 기준 동지 약 10,000 / 하지 약 14,800. ASOS 일사량으로 바꿀 수 있다.
	 */
	public double outdoorDiffuseLux(double latitude, double longitude, LocalDate date) {
		var noon = solar.solarNoon(date, latitude, longitude, SEOUL);
		double alt = solar.position(noon.toInstant(), latitude, longitude).altitudeDeg();
		return 5000 + 10000 * Math.sin(Math.toRadians(Math.max(alt, 0)));
	}

	// ----- 도형 도우미 -----

	static List<double[]> cellCenters(List<Point> polygon, double cellSize) {
		double minX = polygon.stream().mapToDouble(Point::x).min().orElse(0);
		double maxX = polygon.stream().mapToDouble(Point::x).max().orElse(0);
		double minY = polygon.stream().mapToDouble(Point::y).min().orElse(0);
		double maxY = polygon.stream().mapToDouble(Point::y).max().orElse(0);
		double[][] poly = polygon.stream().map(p -> new double[] { p.x(), p.y() }).toArray(double[][]::new);
		List<double[]> centers = new ArrayList<>();
		for (double y = minY + cellSize / 2; y < maxY; y += cellSize) {
			for (double x = minX + cellSize / 2; x < maxX; x += cellSize) {
				if (insidePolygon(x, y, poly)) {
					centers.add(new double[] { x, y });
				}
			}
		}
		return centers;
	}

	static boolean insidePolygon(double x, double y, double[][] poly) {
		boolean inside = false;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			double xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
			if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
				inside = !inside;
			}
		}
		return inside;
	}

	private static boolean properIntersect(double ax, double ay, double bx, double by, double cx, double cy, double dx,
			double dy) {
		double d1 = cross(cx, cy, dx, dy, ax, ay);
		double d2 = cross(cx, cy, dx, dy, bx, by);
		double d3 = cross(ax, ay, bx, by, cx, cy);
		double d4 = cross(ax, ay, bx, by, dx, dy);
		return d1 * d2 < 0 && d3 * d4 < 0;
	}

	private static double cross(double ax, double ay, double bx, double by, double px, double py) {
		return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
	}

	private static double polygonArea(List<Point> polygon) {
		double s = 0;
		for (int i = 0; i < polygon.size(); i++) {
			Point p = polygon.get(i);
			Point q = polygon.get((i + 1) % polygon.size());
			s += p.x() * q.y() - q.x() * p.y();
		}
		return s / 2;
	}

	private static double distanceToWindows(RoomGeom room, double[] p) {
		double best = Double.MAX_VALUE;
		for (WindowGeom w : room.windows()) {
			double vx = w.endX() - w.startX(), vy = w.endY() - w.startY();
			double t = ((p[0] - w.startX()) * vx + (p[1] - w.startY()) * vy) / (vx * vx + vy * vy);
			t = Math.max(0, Math.min(1, t));
			best = Math.min(best, Math.hypot(p[0] - (w.startX() + vx * t), p[1] - (w.startY() + vy * t)));
		}
		return best == Double.MAX_VALUE ? 0 : best;
	}

	static double normalize180(double deg) {
		double r = ((deg + 180) % 360 + 360) % 360 - 180;
		return r;
	}

	private static double round4(double v) {
		return Math.round(v * 10000) / 10000.0;
	}
}
