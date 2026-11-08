// 창문 투영·칸별 햇빛 — 서버 SunlightCalculator.java 와 같은 방법. [② 브라우저 프로그램]
(function (global) {
	const rad = Math.PI / 180, deg = 180 / Math.PI;
	const GLASS = 0.75, BUILDING_REFLECT = 0.2, PATCH = 20;
	const norm360 = (d) => ((d % 360) + 360) % 360;
	const norm180 = (d) => norm360(d + 180) - 180;

	function signedArea(pts) {
		let s = 0;
		pts.forEach((p, i) => { const q = pts[(i + 1) % pts.length]; s += p.x * q.y - q.x * p.y; });
		return s / 2;
	}

	/** 창 기하: 시작점, 벽 방향, 바깥 법선, 방위각 */
	function windowGeom(room, w) {
		const pts = room.points, a = pts[w.wallIndex], b = pts[(w.wallIndex + 1) % pts.length];
		const len = Math.hypot(b.x - a.x, b.y - a.y) || 1, ux = (b.x - a.x) / len, uy = (b.y - a.y) / len;
		const area = signedArea(pts);
		const nx = area > 0 ? uy : -uy, ny = area > 0 ? -ux : ux;
		const azimuth = norm360(room.orientationDeg + Math.atan2(nx, -ny) * deg);
		const sx = a.x + ux * w.offset, sy = a.y + uy * w.offset;
		return { ...w, sx, sy, ex: sx + ux * w.width, ey: sy + uy * w.width, ux, uy, nx, ny, azimuth, wallLength: len };
	}

	/** 방향별 가림 각도 (72칸, 5°) */
	function obstructionAt(obs, az) {
		if (!obs) return 0;
		const i = Math.min(71, Math.floor(norm360(az) / 5));
		return obs[i] == null ? 0 : obs[i];
	}

	function towardSun(orientation, sunAz) {
		const r = (sunAz - orientation) * rad;
		return [Math.sin(r), -Math.cos(r)];
	}

	/** 이 순간 창으로 든 직사광이 작업 높이에 닿는 사각형 [[x,y]x4], 없으면 null */
	function sunPatch(room, g, sun, obs, workHeight = 0) {
		if (sun.altitude <= 0) return null;
		if (Math.abs(norm180(sun.azimuth - g.azimuth)) >= 90) return null;
		if (sun.altitude <= obstructionAt(obs, sun.azimuth)) return null;
		const low = Math.max(g.sill, workHeight), high = g.sill + g.height;
		if (high <= workHeight) return null;
		const u = towardSun(room.orientationDeg, sun.azimuth), cot = 1 / Math.tan(sun.altitude * rad);
		const d1 = (low - workHeight) * cot, d2 = (high - workHeight) * cot;
		return [[g.sx - u[0] * d1, g.sy - u[1] * d1], [g.ex - u[0] * d1, g.ey - u[1] * d1],
			[g.ex - u[0] * d2, g.ey - u[1] * d2], [g.sx - u[0] * d2, g.sy - u[1] * d2]];
	}

	function inside(x, y, poly) {
		let c = false;
		for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			const xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
			if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
		}
		return c;
	}

	function cross(ax, ay, bx, by, px, py) { return (bx - ax) * (py - ay) - (by - ay) * (px - ax); }
	function properIntersect(a, b, c, d) {
		const d1 = cross(c[0], c[1], d[0], d[1], a[0], a[1]), d2 = cross(c[0], c[1], d[0], d[1], b[0], b[1]);
		const d3 = cross(a[0], a[1], b[0], b[1], c[0], c[1]), d4 = cross(a[0], a[1], b[0], b[1], d[0], d[1]);
		return d1 * d2 < 0 && d3 * d4 < 0;
	}

	/** 칸에서 해 쪽으로 창 벽까지 가는 길이 다른 벽을 지나지 않는가 */
	function reaches(room, g, c, u) {
		const denom = u[0] * g.nx + u[1] * g.ny;
		if (denom <= 1e-9) return false;
		const t = ((g.sx - c[0]) * g.nx + (g.sy - c[1]) * g.ny) / denom;
		const q = [c[0] + u[0] * t, c[1] + u[1] * t], pts = room.points;
		for (let i = 0; i < pts.length; i++) {
			if (i === g.wallIndex) continue;
			const a = pts[i], b = pts[(i + 1) % pts.length];
			if (properIntersect(c, q, [a.x, a.y], [b.x, b.y])) return false;
		}
		return true;
	}

	/** 하늘 성분 (CIE 흐린 하늘) */
	function skyComponent(room, geoms, c, obs, workHeight) {
		let sum = 0;
		for (const w of geoms) {
			const nu = Math.max(1, Math.ceil(w.width / PATCH)), nv = Math.max(1, Math.ceil(w.height / PATCH));
			const du = w.width / nu, dv = w.height / nv;
			for (let i = 0; i < nu; i++) {
				const px = w.sx + w.ux * (i + 0.5) * du, py = w.sy + w.uy * (i + 0.5) * du;
				const hx = px - c[0], hy = py - c[1], horiz = Math.hypot(hx, hy);
				const out = hx * w.nx + hy * w.ny;
				if (out <= 0) continue;
				const az = room.orientationDeg + Math.atan2(hx, -hy) * deg;
				for (let j = 0; j < nv; j++) {
					const z = w.sill + (j + 0.5) * dv - workHeight;
					if (z <= 0) continue;
					const r2 = horiz * horiz + z * z, r = Math.sqrt(r2), elev = Math.atan2(z, horiz) * deg;
					let L = (1 + 2 * Math.sin(elev * rad)) / 3 * 9 / (7 * Math.PI);
					if (elev <= obstructionAt(obs, az)) L *= BUILDING_REFLECT;
					sum += L * du * dv * (out / r) * (z / r) / r2;
				}
			}
		}
		return sum;
	}

	function coefficientC(o) {
		const t = [39, 35, 31, 25, 20, 14, 10, 7, 5], idx = Math.max(0, Math.min(8, o / 10)), lo = Math.floor(idx), hi = Math.min(8, lo + 1);
		return t[lo] + (t[hi] - t[lo]) * (idx - lo);
	}

	/** 내부 반사 성분 (BRE 간이식) */
	function internallyReflected(room, geoms, obs) {
		if (!geoms.length) return 0;
		let wa = 0, wc = 0;
		for (const w of geoms) {
			const a = w.width * w.height; let s = 0, n = 0;
			for (let d = -45; d <= 45; d += 5) { s += obstructionAt(obs, w.azimuth + d); n++; }
			wa += a; wc += a * coefficientC(s / n);
		}
		const floor = Math.abs(signedArea(room.points));
		let per = 0;
		room.points.forEach((p, i) => { const q = room.points[(i + 1) % room.points.length]; per += Math.hypot(q.x - p.x, q.y - p.y); });
		const total = 2 * floor + per * room.ceilingHeight;
		return 0.85 * wa / (total * 0.5) * (wc / wa * 0.3 + 5 * 0.7) / 100;
	}

	function outdoorLux(dateStr, lat, lon) {
		const t = Solar.sunTimes(dateStr, lat, lon);
		const alt = Solar.position(Solar.koreaMs(dateStr, t.solarNoon), lat, lon).altitude;
		return 5000 + 10000 * Math.sin(Math.max(alt, 0) * rad);
	}

	function level(lux, direct) {
		if (direct >= 120 || lux >= 1500) return 'BRIGHT';
		if (direct >= 30 || lux >= 800) return 'MEDIUM';
		return 'DIM';
	}

	/** 점들의 하루 직사광(분)과 하늘빛 lux */
	function dayAt(room, points, dateStr, lat, lon, obs, opt = {}) {
		const step = opt.step || 15, work = opt.workHeight || 0;
		const geoms = room.windows.map(w => windowGeom(room, w));
		const direct = new Array(points.length).fill(0);
		for (let m = 0; m < 1440; m += step) {
			const sun = Solar.position(Solar.koreaMs(dateStr, m), lat, lon);
			if (sun.altitude <= 0) continue;
			const u = towardSun(room.orientationDeg, sun.azimuth);
			const lit = new Array(points.length).fill(false);
			for (const g of geoms) {
				const quad = sunPatch(room, g, sun, obs, work);
				if (!quad) continue;
				points.forEach((p, i) => { if (!lit[i] && inside(p[0], p[1], quad) && reaches(room, g, p, u)) lit[i] = true; });
			}
			lit.forEach((v, i) => { if (v) direct[i] += step; });
		}
		const out = outdoorLux(dateStr, lat, lon), irc = internallyReflected(room, geoms, obs);
		const tr = GLASS * (room.curtainT ?? 1);
		return points.map((p, i) => {
			const lux = (skyComponent(room, geoms, p, obs, work) + irc) * tr * out;
			let dist = Infinity;
			geoms.forEach(g => {
				const vx = g.ex - g.sx, vy = g.ey - g.sy, t = Math.max(0, Math.min(1, ((p[0] - g.sx) * vx + (p[1] - g.sy) * vy) / (vx * vx + vy * vy)));
				dist = Math.min(dist, Math.hypot(p[0] - (g.sx + vx * t), p[1] - (g.sy + vy * t)));
			});
			return { x: p[0], y: p[1], directMinutes: direct[i], lux, distance: dist === Infinity ? 0 : dist, level: level(lux, direct[i]) };
		});
	}

	/** 25cm 칸 중심 */
	function cellCenters(room, size = 25) {
		const xs = room.points.map(p => p.x), ys = room.points.map(p => p.y);
		const poly = room.points.map(p => [p.x, p.y]), out = [];
		for (let y = Math.min(...ys) + size / 2; y < Math.max(...ys); y += size)
			for (let x = Math.min(...xs) + size / 2; x < Math.max(...xs); x += size)
				if (inside(x, y, poly)) out.push([x, y]);
		return out;
	}

	/** 식물 점수 — 서버 PlantScorer.score 와 같은 식 */
	function plantScore(p, c) {
		let light = 1;
		if (c.lux < p.minLux) light = Math.pow(Math.max(c.lux, 1) / p.minLux, 0.7);
		else if (c.lux > p.maxLux) light = Math.pow(p.maxLux / c.lux, 0.5);
		let direct = 1;
		if (p.minDirectMinutes > 0 && c.directMinutes < p.minDirectMinutes) direct = 0.3 + 0.7 * c.directMinutes / p.minDirectMinutes;
		if (p.maxDirectMinutes != null && c.directMinutes > p.maxDirectMinutes) direct *= Math.pow(Math.max(p.maxDirectMinutes, 15) / c.directMinutes, 0.7);
		const within = p.minWindowDistance == null || p.maxWindowDistance == null || (c.distance >= p.minWindowDistance && c.distance <= p.maxWindowDistance);
		return Math.round(100 * light * direct * (within ? 1 : 0.85));
	}

	global.Light = { windowGeom, sunPatch, dayAt, cellCenters, plantScore, signedArea, inside, obstructionAt, norm360 };
})(window);
