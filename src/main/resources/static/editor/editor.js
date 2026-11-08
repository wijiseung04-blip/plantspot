// 평면도 햇빛 에디터 [② DB와 무관한 브라우저 프로그램]
// 방 그리기 · 창문 배치 · 날짜/시각 슬라이더로 햇빛 이동 · 식물 끌어다 놓기 · 보정 전후 겹쳐 보기
(function () {
	const $ = (id) => document.getElementById(id);
	const STORE_KEY = 'plantspot-editor-v1';
	const HADAN = { lat: 35.1154, lon: 128.9605 };
	const GENERIC_PLANTS = [
		{ name: '낮은 광도 식물', minLux: 300, maxLux: 800, minDirectMinutes: 0, maxDirectMinutes: 60 },
		{ name: '중간 광도 식물', minLux: 800, maxLux: 1500, minDirectMinutes: 0, maxDirectMinutes: 180 },
		{ name: '높은 광도 식물', minLux: 1500, maxLux: 10000, minDirectMinutes: 120, maxDirectMinutes: null }
	];
	const SEASONS = [['춘분', '03-20'], ['하지', '06-21'], ['추분', '09-23'], ['동지', '12-22']];

	const canvas = $('canvas'), ctx = canvas.getContext('2d');
	const body = document.body;
	const roomId = new URLSearchParams(location.search).get('roomId');
	const loggedIn = body.dataset.auth === 'true';

	let state = defaultState();
	let view = { scale: 1, ox: 0, oy: 0 };
	let mode = 'select', drag = null, selected = null, drawPts = [], hover = null;
	let heat = null, heatKey = '', playTimer = null;
	let plantCatalog = GENERIC_PLANTS.slice();

	function defaultState() {
		return {
			name: '새 방', orientationDeg: 0, ceilingHeight: 230, curtainT: 1,
			points: [{ x: 0, y: 0 }, { x: 400, y: 0 }, { x: 400, y: 350 }, { x: 0, y: 350 }],
			windows: [{ wallIndex: 2, offset: 110, width: 180, height: 150, sill: 60 }],
			lat: HADAN.lat, lon: HADAN.lon, frontObstruction: 0,
			obstruction: null, sensor: null,
			plants: [], date: new Date().toISOString().slice(0, 10), minutes: 12 * 60, workHeight: 0
		};
	}

	// ----- 상태 → 계산용 -----

	function room() {
		return { points: state.points, orientationDeg: state.orientationDeg, ceilingHeight: state.ceilingHeight,
			curtainT: state.curtainT, windows: state.windows };
	}

	/** 쓰는 가림 각도: 서버 자료(건물+실측) 또는 '앞 건물 각도' 직접 입력 */
	function profile(calibrated = true) {
		if (state.obstruction) {
			return state.obstruction.map((b, i) => calibrated && state.sensor && state.sensor[i] != null ? state.sensor[i] : b);
		}
		if (!state.frontObstruction) return null;
		// 창 방향 ±60° 범위에 같은 각도
		const arr = new Array(72).fill(0);
		state.windows.forEach(w => {
			const az = Light.windowGeom(room(), w).azimuth;
			for (let d = -60; d <= 60; d += 5) arr[Math.floor(Light.norm360(az + d) / 5) % 72] = state.frontObstruction;
		});
		return arr;
	}

	const hasSensor = () => !!(state.sensor && state.sensor.some(v => v != null));

	// ----- 화면 좌표 -----

	function fit() {
		const r = canvas.getBoundingClientRect(), dpr = window.devicePixelRatio || 1;
		canvas.width = r.width * dpr; canvas.height = r.height * dpr;
		const xs = state.points.map(p => p.x), ys = state.points.map(p => p.y);
		const w = Math.max(...xs) - Math.min(...xs) || 400, h = Math.max(...ys) - Math.min(...ys) || 400;
		const scale = Math.min(r.width / (w + 160), r.height / (h + 160));
		view = { scale, ox: r.width / 2 - (Math.min(...xs) + w / 2) * scale, oy: r.height / 2 - (Math.min(...ys) + h / 2) * scale, dpr };
	}
	const toScreen = (x, y) => [x * view.scale + view.ox, y * view.scale + view.oy];
	const toRoom = (sx, sy) => [(sx - view.ox) / view.scale, (sy - view.oy) / view.scale];
	const snap = (v) => Math.round(v / 5) * 5;

	// ----- 그리기 -----

	function css(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888'; }

	function path(pts) {
		ctx.beginPath();
		pts.forEach((p, i) => { const [x, y] = toScreen(p[0] ?? p.x, p[1] ?? p.y); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
		ctx.closePath();
	}

	function draw() {
		const r = canvas.getBoundingClientRect();
		ctx.setTransform(view.dpr, 0, 0, view.dpr, 0, 0);
		ctx.clearRect(0, 0, r.width, r.height);
		const text = css('--text'), muted = css('--muted'), line = css('--line'), accent = css('--accent');

		// 격자 50cm
		ctx.strokeStyle = line; ctx.lineWidth = 0.5;
		const [x0, y0] = toRoom(0, 0), [x1, y1] = toRoom(r.width, r.height);
		for (let x = Math.floor(x0 / 50) * 50; x < x1; x += 50) { const [sx] = toScreen(x, 0); ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, r.height); ctx.stroke(); }
		for (let y = Math.floor(y0 / 50) * 50; y < y1; y += 50) { const [, sy] = toScreen(0, y); ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(r.width, sy); ctx.stroke(); }

		if (state.points.length >= 3) {
			// 방 바닥
			path(state.points); ctx.fillStyle = css('--surface'); ctx.fill();
			ctx.save(); path(state.points); ctx.clip();
			// 하루 직사광 지도
			if ($('show-heat').checked && heat) {
				const max = Math.max(60, ...heat.map(c => c.directMinutes)), s = 25 * view.scale;
				heat.forEach(c => {
					const [sx, sy] = toScreen(c.x, c.y);
					ctx.fillStyle = c.directMinutes > 0 ? `rgba(245,180,0,${0.12 + 0.6 * c.directMinutes / max})` : `rgba(80,110,140,${Math.max(0, 0.35 - c.lux / 6000)})`;
					ctx.fillRect(sx - s / 2, sy - s / 2, s, s);
				});
			}
			// 지금 시각 햇빛
			const sun = Solar.position(Solar.koreaMs(state.date, state.minutes), state.lat, state.lon);
			const geoms = state.windows.map(w => Light.windowGeom(room(), w));
			if (hasSensor() && $('show-before').checked) {
				ctx.setLineDash([6, 4]); ctx.strokeStyle = '#d97706'; ctx.lineWidth = 2;
				geoms.forEach(g => { const q = Light.sunPatch(room(), g, sun, profile(false), state.workHeight); if (q) { path(q); ctx.stroke(); } });
				ctx.setLineDash([]);
			}
			geoms.forEach(g => {
				const q = Light.sunPatch(room(), g, sun, profile(true), state.workHeight);
				if (q) { path(q); ctx.fillStyle = 'rgba(255, 200, 40, 0.55)'; ctx.fill(); }
			});
			ctx.restore();

			// 벽과 길이
			path(state.points); ctx.strokeStyle = text; ctx.lineWidth = 3; ctx.stroke();
			ctx.font = '11px system-ui'; ctx.fillStyle = muted; ctx.textAlign = 'center';
			state.points.forEach((p, i) => {
				const q = state.points[(i + 1) % state.points.length];
				const [sx, sy] = toScreen((p.x + q.x) / 2, (p.y + q.y) / 2);
				const len = Math.round(Math.hypot(q.x - p.x, q.y - p.y));
				const g = Light.windowGeom(room(), { wallIndex: i, offset: 0, width: 1, height: 1, sill: 0 });
				ctx.fillText(`${len}cm`, sx + g.nx * 16, sy + g.ny * 16 + 4);
				if (selected && selected.type === 'wall' && selected.index === i) {
					const [ax, ay] = toScreen(p.x, p.y), [bx, by] = toScreen(q.x, q.y);
					ctx.strokeStyle = accent; ctx.lineWidth = 6; ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.stroke();
				}
			});
			// 창문
			geoms.forEach((g, i) => {
				const [ax, ay] = toScreen(g.sx, g.sy), [bx, by] = toScreen(g.ex, g.ey);
				ctx.strokeStyle = selected && selected.type === 'window' && selected.index === i ? accent : '#4aa3df';
				ctx.lineWidth = 7; ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.stroke();
			});
			// 꼭짓점
			state.points.forEach((p, i) => {
				const [sx, sy] = toScreen(p.x, p.y);
				ctx.fillStyle = selected && selected.type === 'vertex' && selected.index === i ? accent : text;
				ctx.beginPath(); ctx.arc(sx, sy, 5, 0, Math.PI * 2); ctx.fill();
			});
		}

		// 그리는 중인 선
		if (mode === 'draw' && drawPts.length) {
			ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.beginPath();
			drawPts.forEach((p, i) => { const [x, y] = toScreen(p.x, p.y); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
			if (hover) { const [x, y] = toScreen(hover[0], hover[1]); ctx.lineTo(x, y); }
			ctx.stroke();
			drawPts.forEach(p => { const [x, y] = toScreen(p.x, p.y); ctx.fillStyle = accent; ctx.beginPath(); ctx.arc(x, y, 4, 0, Math.PI * 2); ctx.fill(); });
		}

		// 식물
		state.plants.forEach((p, i) => {
			const [sx, sy] = toScreen(p.x, p.y), score = p.score ?? null;
			ctx.fillStyle = score == null ? accent : `hsl(${Math.max(0, Math.min(120, score * 1.2))}, 70%, 45%)`;
			ctx.beginPath(); ctx.arc(sx, sy, 13, 0, Math.PI * 2); ctx.fill();
			ctx.strokeStyle = selected && selected.type === 'plant' && selected.index === i ? text : '#fff'; ctx.lineWidth = 3; ctx.stroke();
			ctx.fillStyle = '#fff'; ctx.font = 'bold 11px system-ui'; ctx.textAlign = 'center';
			ctx.fillText(score == null ? '🌱' : String(score), sx, sy + 4);
			ctx.fillStyle = text; ctx.font = '11px system-ui'; ctx.fillText(p.name, sx, sy + 28);
		});

		drawCompass();
	}

	function drawCompass() {
		const cx = 52, cy = 52, R = 36;
		ctx.save();
		ctx.fillStyle = css('--surface'); ctx.strokeStyle = css('--line'); ctx.lineWidth = 1;
		ctx.beginPath(); ctx.arc(cx, cy, R + 8, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
		const north = -state.orientationDeg * Math.PI / 180; // 화면 위 = orientation → 북쪽은 그만큼 반대로
		ctx.strokeStyle = css('--accent'); ctx.lineWidth = 2;
		ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.sin(north) * R, cy - Math.cos(north) * R); ctx.stroke();
		ctx.fillStyle = css('--accent'); ctx.font = 'bold 12px system-ui'; ctx.textAlign = 'center';
		ctx.fillText('N', cx + Math.sin(north) * (R + 1), cy - Math.cos(north) * (R + 1) + 4);
		const sun = Solar.position(Solar.koreaMs(state.date, state.minutes), state.lat, state.lon);
		if (sun.altitude > 0) {
			const a = (sun.azimuth - state.orientationDeg) * Math.PI / 180, r = R * (1 - sun.altitude / 90);
			ctx.fillStyle = '#f5b400'; ctx.beginPath(); ctx.arc(cx + Math.sin(a) * r, cy - Math.cos(a) * r, 6, 0, Math.PI * 2); ctx.fill();
		}
		ctx.restore();
	}

	// ----- 계산 (하루 지도, 식물 점수) -----

	function recompute() {
		const key = JSON.stringify([state.points, state.windows, state.orientationDeg, state.curtainT, state.date, state.lat, state.lon,
			state.frontObstruction, state.obstruction, state.sensor, state.ceilingHeight, state.workHeight]);
		if (key === heatKey) return;
		heatKey = key;
		if (state.points.length < 3) { heat = null; return; }
		heat = Light.dayAt(room(), Light.cellCenters(room()), state.date, state.lat, state.lon, profile(true), { workHeight: state.workHeight });
		scorePlants();
		const max = Math.max(0, ...heat.map(c => c.directMinutes));
		$('day-info').textContent = `이 날 가장 오래 드는 칸 ${hm(max)} · 직사광 드는 칸 ${Math.round(heat.filter(c => c.directMinutes > 0).length / heat.length * 100)}%`;
	}

	function scorePlants() {
		if (!heat || !state.plants.length) { renderPlantList(); return; }
		const pts = state.plants.map(p => [p.x, p.y]);
		const at = Light.dayAt(room(), pts, state.date, state.lat, state.lon, profile(true), { workHeight: state.workHeight });
		state.plants.forEach((p, i) => { p.cell = at[i]; p.score = Light.plantScore(p, at[i]); });
		renderPlantList();
	}

	function seasonScores(p) {
		const year = state.date.slice(0, 4);
		return SEASONS.map(([label, md]) => {
			const c = Light.dayAt(room(), [[p.x, p.y]], `${year}-${md}`, state.lat, state.lon, profile(true), { workHeight: state.workHeight })[0];
			return `${label} ${Light.plantScore(p, c)}점`;
		}).join(' · ');
	}

	// ----- 패널 -----

	const hm = (m) => `${Math.floor(m / 60)}시간 ${String(m % 60).padStart(2, '0')}분`;
	const clock = (m) => m == null ? '-' : `${String(Math.floor(m / 60) % 24).padStart(2, '0')}:${String(Math.round(m % 60)).padStart(2, '0')}`;

	function renderTime() {
		$('date').value = state.date;
		$('time').value = state.minutes;
		$('time-label').textContent = clock(state.minutes);
		const sun = Solar.position(Solar.koreaMs(state.date, state.minutes), state.lat, state.lon);
		const t = Solar.sunTimes(state.date, state.lat, state.lon);
		$('sun-info').textContent = sun.altitude > 0
			? `해 고도 ${sun.altitude.toFixed(1)}° · 방위각 ${sun.azimuth.toFixed(0)}° · 일출 ${clock(t.sunrise)} · 일몰 ${clock(t.sunset)}`
			: `해가 졌습니다 · 일출 ${clock(t.sunrise)} · 일몰 ${clock(t.sunset)}`;
	}

	function renderRoomFields() {
		$('room-name').value = state.name;
		$('orientation').value = state.orientationDeg;
		$('orientation-label').textContent = `${Math.round(state.orientationDeg)}°`;
		$('ceiling').value = state.ceilingHeight;
		$('curtain').value = String(state.curtainT);
		$('work-height').value = state.workHeight;
		$('lat').value = Number(state.lat).toFixed(4); $('lon').value = Number(state.lon).toFixed(4);
		$('front-obs').value = state.frontObstruction; $('front-obs-label').textContent = `${state.frontObstruction}°`;
		$('obs-manual').hidden = !!state.obstruction;
		$('obs-server').hidden = !state.obstruction;
		$('before-box').hidden = !hasSensor();
	}

	function renderSelection() {
		const box = $('selection');
		if (!selected) { box.innerHTML = '<p class="muted">벽·창문·꼭짓점·식물을 눌러 고릅니다. 벽을 고르면 창문을 달 수 있습니다.</p>'; return; }
		if (selected.type === 'wall') {
			const g = Light.windowGeom(room(), { wallIndex: selected.index, offset: 0, width: 1, height: 1, sill: 0 });
			box.innerHTML = `<p><strong>${selected.index}번 벽</strong> · ${Math.round(g.wallLength)}cm · 바깥 방향 ${Math.round(g.azimuth)}°</p>
				<button type="button" class="small" id="add-window">이 벽에 창문 달기</button>
				<button type="button" class="small secondary" id="split-wall">벽 가운데에 꼭짓점 넣기</button>`;
			$('add-window').onclick = () => {
				const w = Math.min(180, Math.max(40, g.wallLength - 40));
				state.windows.push({ wallIndex: selected.index, offset: Math.max(0, (g.wallLength - w) / 2), width: w, height: 150, sill: 60 });
				selected = { type: 'window', index: state.windows.length - 1 }; changed();
			};
			$('split-wall').onclick = () => splitWall(selected.index);
		} else if (selected.type === 'window') {
			const w = state.windows[selected.index], g = Light.windowGeom(room(), w);
			box.innerHTML = `<p><strong>창문</strong> · ${Math.round(g.azimuth)}° 방향</p>
				<div class="row">
					<label>벽 시작점에서(cm) <input type="number" id="w-offset" value="${Math.round(w.offset)}" min="0"></label>
					<label>폭(cm) <input type="number" id="w-width" value="${w.width}" min="20"></label>
					<label>높이(cm) <input type="number" id="w-height" value="${w.height}" min="20"></label>
					<label>창턱(cm) <input type="number" id="w-sill" value="${w.sill}" min="0"></label>
				</div>
				<button type="button" class="small danger" id="w-del">창문 지우기</button>`;
			['offset', 'width', 'height', 'sill'].forEach(k => $('w-' + k).onchange = (ev) => {
				w[k] = Math.max(0, Number(ev.target.value)); clampWindow(w); changed();
			});
			$('w-del').onclick = () => { state.windows.splice(selected.index, 1); selected = null; changed(); };
		} else if (selected.type === 'vertex') {
			const p = state.points[selected.index];
			box.innerHTML = `<p><strong>꼭짓점</strong> (${Math.round(p.x)}, ${Math.round(p.y)})cm</p>
				<button type="button" class="small danger" id="v-del" ${state.points.length <= 3 ? 'disabled' : ''}>꼭짓점 지우기</button>`;
			$('v-del').onclick = () => deleteVertex(selected.index);
		} else if (selected.type === 'plant') {
			const p = state.plants[selected.index];
			box.innerHTML = `<p><strong>${escapeHtml(p.name)}</strong> (${Math.round(p.x)}, ${Math.round(p.y)})cm</p>
				<p class="muted">${p.cell ? `이 날 직사광 ${hm(p.cell.directMinutes)} · 하늘빛 약 ${Math.round(p.cell.lux)} lux · 점수 ${p.score}점` : ''}</p>
				<p class="muted" id="p-seasons">계절별 점수 계산 중…</p>
				<button type="button" class="small danger" id="p-del">식물 빼기</button>`;
			$('p-del').onclick = () => { state.plants.splice(selected.index, 1); selected = null; changed(); };
			setTimeout(() => { const el = $('p-seasons'); if (el) el.textContent = '계절별: ' + seasonScores(p); }, 0);
		}
	}

	function renderPlantList() {
		$('placed').innerHTML = state.plants.length ? state.plants.map((p, i) =>
			`<div>${escapeHtml(p.name)} <span class="badge ${p.score != null && p.score < 50 ? 'warn' : ''}">${p.score ?? '-'}점</span>
			<button type="button" class="link" data-pick="${i}">고르기</button></div>`).join('') : '<p class="muted">왼쪽 목록에서 식물을 끌어다 방 안에 놓으세요.</p>';
	}

	function renderPalette() {
		$('palette').innerHTML = plantCatalog.map((p, i) =>
			`<div class="chip" draggable="true" data-plant="${i}" title="${p.minLux}~${p.maxLux} lux">${escapeHtml(p.name)}</div>`).join('');
	}

	// ----- 편집 동작 -----

	function clampWindow(w) {
		const g = Light.windowGeom(room(), w);
		w.width = Math.min(w.width, g.wallLength);
		w.offset = Math.max(0, Math.min(w.offset, g.wallLength - w.width));
		w.height = Math.min(w.height, state.ceilingHeight - w.sill);
	}

	function splitWall(i) {
		const p = state.points[i], q = state.points[(i + 1) % state.points.length];
		state.points.splice(i + 1, 0, { x: snap((p.x + q.x) / 2), y: snap((p.y + q.y) / 2) });
		state.windows.forEach(w => { if (w.wallIndex > i) w.wallIndex++; else if (w.wallIndex === i) clampWindow(w); });
		selected = { type: 'vertex', index: i + 1 }; changed();
	}

	function deleteVertex(i) {
		if (state.points.length <= 3) return;
		state.points.splice(i, 1);
		// 지운 꼭짓점에 붙은 벽의 창문은 지우고 번호를 당긴다
		state.windows = state.windows.filter(w => w.wallIndex !== i && w.wallIndex !== (i - 1 + state.points.length + 1) % (state.points.length + 1))
			.map(w => ({ ...w, wallIndex: w.wallIndex > i ? w.wallIndex - 1 : w.wallIndex }));
		state.windows.forEach(clampWindow);
		selected = null; changed();
	}

	function preset(kind) {
		if (kind === 'rect') state.points = [{ x: 0, y: 0 }, { x: 400, y: 0 }, { x: 400, y: 350 }, { x: 0, y: 350 }];
		if (kind === 'l') state.points = [{ x: 0, y: 0 }, { x: 250, y: 0 }, { x: 250, y: 200 }, { x: 500, y: 200 }, { x: 500, y: 400 }, { x: 0, y: 400 }];
		state.windows = [{ wallIndex: kind === 'l' ? 4 : 2, offset: 100, width: 180, height: 150, sill: 60 }];
		selected = null; fit(); changed();
	}

	function hit(sx, sy) {
		for (let i = state.plants.length - 1; i >= 0; i--) {
			const [x, y] = toScreen(state.plants[i].x, state.plants[i].y);
			if (Math.hypot(sx - x, sy - y) < 15) return { type: 'plant', index: i };
		}
		for (let i = 0; i < state.points.length; i++) {
			const [x, y] = toScreen(state.points[i].x, state.points[i].y);
			if (Math.hypot(sx - x, sy - y) < 9) return { type: 'vertex', index: i };
		}
		const [rx, ry] = toRoom(sx, sy), tol = 10 / view.scale;
		for (let i = 0; i < state.windows.length; i++) {
			const g = Light.windowGeom(room(), state.windows[i]);
			if (segDist(rx, ry, g.sx, g.sy, g.ex, g.ey) < tol) return { type: 'window', index: i };
		}
		for (let i = 0; i < state.points.length; i++) {
			const p = state.points[i], q = state.points[(i + 1) % state.points.length];
			if (segDist(rx, ry, p.x, p.y, q.x, q.y) < tol) return { type: 'wall', index: i };
		}
		return null;
	}

	function segDist(px, py, ax, ay, bx, by) {
		const vx = bx - ax, vy = by - ay, t = Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy || 1)));
		return Math.hypot(px - (ax + vx * t), py - (ay + vy * t));
	}

	function pointer(ev) { const r = canvas.getBoundingClientRect(); return [ev.clientX - r.left, ev.clientY - r.top]; }

	canvas.addEventListener('pointerdown', (ev) => {
		const [sx, sy] = pointer(ev), [rx, ry] = toRoom(sx, sy);
		if (mode === 'draw') {
			const p = { x: snap(rx), y: snap(ry) };
			if (drawPts.length >= 3) {
				const [fx, fy] = toScreen(drawPts[0].x, drawPts[0].y);
				if (Math.hypot(sx - fx, sy - fy) < 12) { finishDraw(); return; }
			}
			drawPts.push(p); draw(); return;
		}
		const h = hit(sx, sy);
		selected = h;
		if (h && (h.type === 'vertex' || h.type === 'plant' || h.type === 'window')) {
			drag = { ...h, startX: rx, startY: ry, orig: h.type === 'window' ? state.windows[h.index].offset : null };
			canvas.setPointerCapture(ev.pointerId);
		}
		renderSelection(); draw();
	});

	canvas.addEventListener('pointermove', (ev) => {
		const [sx, sy] = pointer(ev), [rx, ry] = toRoom(sx, sy);
		if (mode === 'draw') { hover = [snap(rx), snap(ry)]; draw(); return; }
		if (!drag) return;
		if (drag.type === 'vertex') { state.points[drag.index] = { x: snap(rx), y: snap(ry) }; state.windows.forEach(clampWindow); }
		if (drag.type === 'plant') { state.plants[drag.index].x = Math.round(rx); state.plants[drag.index].y = Math.round(ry); state.plants[drag.index].score = null; }
		if (drag.type === 'window') {
			const w = state.windows[drag.index], g = Light.windowGeom(room(), w);
			w.offset = snap(drag.orig + (rx - drag.startX) * g.ux + (ry - drag.startY) * g.uy); clampWindow(w);
		}
		draw();
	});

	canvas.addEventListener('pointerup', () => { if (drag) { drag = null; changed(); } });

	canvas.addEventListener('dblclick', (ev) => {
		const [sx, sy] = pointer(ev);
		if (mode === 'draw') { finishDraw(); return; }
		const h = hit(sx, sy);
		if (h && h.type === 'wall') splitWall(h.index);
	});

	document.addEventListener('keydown', (ev) => {
		if (ev.target.tagName === 'INPUT' || ev.target.tagName === 'SELECT') return;
		if ((ev.key === 'Delete' || ev.key === 'Backspace') && selected) {
			if (selected.type === 'vertex') deleteVertex(selected.index);
			else if (selected.type === 'window') { state.windows.splice(selected.index, 1); selected = null; changed(); }
			else if (selected.type === 'plant') { state.plants.splice(selected.index, 1); selected = null; changed(); }
		}
		if (ev.key === 'Escape' && mode === 'draw') { mode = 'select'; drawPts = []; $('draw').textContent = '직접 그리기'; draw(); }
	});

	function finishDraw() {
		if (drawPts.length < 3) return;
		const area = Math.abs(Light.signedArea(drawPts));
		if (area < 10000) { alert('방이 너무 작습니다 (1㎡ 이상).'); return; }
		state.points = drawPts; state.windows = []; drawPts = []; mode = 'select';
		$('draw').textContent = '직접 그리기'; selected = null; fit(); changed();
		alert('방을 그렸습니다. 벽을 눌러 창문을 다세요.');
	}

	// 식물 끌어다 놓기
	$('palette').addEventListener('dragstart', (ev) => { const i = ev.target.dataset.plant; if (i != null) ev.dataTransfer.setData('text/plain', i); });
	canvas.addEventListener('dragover', (ev) => ev.preventDefault());
	canvas.addEventListener('drop', (ev) => {
		ev.preventDefault();
		const i = Number(ev.dataTransfer.getData('text/plain'));
		const p = plantCatalog[i]; if (!p) return;
		const [rx, ry] = toRoom(...pointer(ev));
		if (!Light.inside(rx, ry, state.points.map(q => [q.x, q.y]))) { alert('방 안에 놓으세요.'); return; }
		state.plants.push({ ...p, x: Math.round(rx), y: Math.round(ry) });
		selected = { type: 'plant', index: state.plants.length - 1 }; changed();
	});
	// 터치 화면용: 식물을 누르고 방을 누르면 놓기
	let pendingPlant = null;
	$('palette').addEventListener('click', (ev) => {
		const i = ev.target.dataset.plant; if (i == null) return;
		pendingPlant = plantCatalog[Number(i)];
		$('palette-hint').textContent = `${pendingPlant.name}: 방 안을 누르면 놓습니다.`;
	});
	canvas.addEventListener('click', (ev) => {
		if (!pendingPlant || mode === 'draw') return;
		const [rx, ry] = toRoom(...pointer(ev));
		if (!Light.inside(rx, ry, state.points.map(q => [q.x, q.y]))) return;
		state.plants.push({ ...pendingPlant, x: Math.round(rx), y: Math.round(ry) });
		pendingPlant = null; $('palette-hint').textContent = '';
		selected = { type: 'plant', index: state.plants.length - 1 }; changed();
	});
	$('placed').addEventListener('click', (ev) => { const i = ev.target.dataset.pick; if (i != null) { selected = { type: 'plant', index: Number(i) }; renderSelection(); draw(); } });

	// ----- 입력 -----

	$('date').onchange = (ev) => { state.date = ev.target.value; changed(); };
	$('time').oninput = (ev) => { state.minutes = Number(ev.target.value); renderTime(); draw(); save(); };
	SEASONS.forEach(([label, md]) => {
		const b = document.createElement('button'); b.type = 'button'; b.className = 'small secondary'; b.textContent = label;
		b.onclick = () => { state.date = `${state.date.slice(0, 4)}-${md}`; changed(); };
		$('season-buttons').appendChild(b);
	});
	$('play').onclick = () => {
		if (playTimer) { clearInterval(playTimer); playTimer = null; $('play').textContent = '▶ 하루 재생'; return; }
		$('play').textContent = '■ 멈춤';
		const t = Solar.sunTimes(state.date, state.lat, state.lon);
		if (state.minutes < (t.sunrise ?? 0) || state.minutes > (t.sunset ?? 1440)) state.minutes = Math.floor((t.sunrise ?? 360) / 5) * 5;
		playTimer = setInterval(() => {
			state.minutes += 5;
			if (state.minutes > (t.sunset ?? 1440)) { state.minutes = Math.floor((t.sunrise ?? 360) / 5) * 5; }
			renderTime(); draw();
		}, 60);
	};
	$('room-name').onchange = (ev) => { state.name = ev.target.value; save(); };
	$('orientation').oninput = (ev) => { state.orientationDeg = Number(ev.target.value); $('orientation-label').textContent = `${state.orientationDeg}°`; changed(); };
	$('ceiling').onchange = (ev) => { state.ceilingHeight = Math.max(180, Number(ev.target.value)); state.windows.forEach(clampWindow); changed(); };
	$('curtain').onchange = (ev) => { state.curtainT = Number(ev.target.value); changed(); };
	$('work-height').onchange = (ev) => { state.workHeight = Math.max(0, Number(ev.target.value)); changed(); };
	$('lat').onchange = (ev) => { state.lat = Number(ev.target.value); changed(); };
	$('lon').onchange = (ev) => { state.lon = Number(ev.target.value); changed(); };
	$('front-obs').oninput = (ev) => { state.frontObstruction = Number(ev.target.value); $('front-obs-label').textContent = `${state.frontObstruction}°`; changed(); };
	$('show-heat').onchange = () => draw();
	$('show-before').onchange = () => draw();
	$('preset-rect').onclick = () => preset('rect');
	$('preset-l').onclick = () => preset('l');
	$('draw').onclick = () => {
		mode = mode === 'draw' ? 'select' : 'draw'; drawPts = [];
		$('draw').textContent = mode === 'draw' ? '그리기 취소' : '직접 그리기';
		if (mode === 'draw') alert('평면도를 눌러 꼭짓점을 찍고, 첫 점을 다시 누르거나 두 번 눌러 마칩니다. (5cm 단위)');
		draw();
	};
	$('fit').onclick = () => { fit(); draw(); };
	$('reset').onclick = () => { if (confirm('처음 상태로 되돌릴까요?')) { state = defaultState(); selected = null; fit(); renderRoomFields(); changed(); } };

	// 저장·내보내기
	function save() { try { if (!roomId) localStorage.setItem(STORE_KEY, JSON.stringify(state)); } catch { /* 저장 불가 환경 */ } }
	$('export-json').onclick = () => download(new Blob([JSON.stringify(state, null, 1)], { type: 'application/json' }), `${state.name || 'room'}.json`);
	$('import-json').onchange = async (ev) => {
		const f = ev.target.files[0]; if (!f) return;
		try { state = { ...defaultState(), ...JSON.parse(await f.text()) }; selected = null; fit(); renderRoomFields(); changed(); }
		catch { alert('파일을 읽을 수 없습니다.'); }
		ev.target.value = '';
	};
	$('export-png').onclick = () => canvas.toBlob(b => download(b, `${state.name || 'room'}-${state.date}-${clock(state.minutes).replace(':', '')}.png`));
	function download(blob, name) { const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); }

	// ----- 서버 연동 (회원, 선택) -----

	async function loadServer() {
		const r = await api('/api/rooms/' + roomId);
		const house = await api('/api/houses/' + r.houseId);
		const obs = await api(`/api/houses/${r.houseId}/obstructions`);
		const curtain = { NONE: 1, SHEER: 0.6, BLIND: 0.5, THICK: 0.2, BLACKOUT: 0.02 }[r.curtain] ?? 1;
		state = { ...state, name: r.name, orientationDeg: r.orientationDeg, ceilingHeight: r.ceilingHeight, curtainT: curtain,
			points: r.shape.map(p => ({ x: p.x, y: p.y })),
			windows: r.windows.map(w => ({ id: w.id, wallIndex: w.wallIndex, offset: w.offset, width: w.width, height: w.height, sill: w.sillHeight })),
			lat: house.latitude, lon: house.longitude, obstruction: obs.map(b => b.buildingAngle), sensor: obs.map(b => b.sensorAngle ?? null),
			serverCurtain: r.curtain };
		try {
			const mine = await api('/api/my-plants');
			state.plants = [];
			for (const m of mine.filter(m => m.roomId === r.id && m.plantId)) {
				const p = await api('/api/plants/' + m.plantId);
				state.plants.push({ ...p, name: m.nickname, myPlantId: m.id, x: m.x, y: m.y });
			}
		} catch { /* 내 식물 없음 */ }
		$('server-box').hidden = false;
		$('server-status').textContent = `${house.name} · ${r.name} 불러옴 (가림 각도: 건물 데이터${hasSensor() ? ' + 센서 실측' : ''})`;
	}

	$('save-server').onclick = async () => {
		if (!roomId) return;
		try {
			await api('/api/rooms/' + roomId, { method: 'PUT', json: { name: state.name, shape: state.points, orientationDeg: state.orientationDeg,
				ceilingHeight: state.ceilingHeight, curtain: state.serverCurtain || 'NONE' } });
			const existing = (await api(`/api/rooms/${roomId}/windows`)).map(w => w.id);
			for (const id of existing) await api('/api/windows/' + id, { method: 'DELETE' });
			for (const w of state.windows) {
				const saved = await api(`/api/rooms/${roomId}/windows`, { method: 'POST', json: { wallIndex: w.wallIndex, offset: w.offset, width: w.width, height: w.height, sillHeight: w.sill } });
				w.id = saved.id;
			}
			for (const p of state.plants.filter(p => p.myPlantId)) {
				await api(`/api/my-plants/${p.myPlantId}/position`, { method: 'PATCH', json: { roomId: Number(roomId), x: p.x, y: p.y } });
			}
			$('server-status').textContent = '서버에 저장했습니다. 계절별 햇빛·추천이 새로 계산됩니다.';
		} catch (e) { alert(e.message); }
	};

	async function loadCatalog(keyword = '') {
		try {
			const r = await api(`/api/plants?size=40&keyword=${encodeURIComponent(keyword)}`);
			plantCatalog = r.items.length ? r.items : GENERIC_PLANTS.slice();
		} catch { plantCatalog = GENERIC_PLANTS.slice(); }
		renderPalette();
	}
	let searchTimer = null;
	$('plant-search').oninput = (ev) => { clearTimeout(searchTimer); searchTimer = setTimeout(() => loadCatalog(ev.target.value), 250); };

	// ----- 시작 -----

	function changed() { recompute(); renderTime(); renderSelection(); renderRoomFields(); renderPlantList(); draw(); save(); }

	async function start() {
		if (roomId && loggedIn) {
			try { await loadServer(); } catch (e) { alert('방을 불러오지 못했습니다: ' + e.message); }
		} else {
			try { const saved = localStorage.getItem(STORE_KEY); if (saved) state = { ...defaultState(), ...JSON.parse(saved) }; } catch { /* 없음 */ }
		}
		fit(); renderRoomFields(); renderPalette(); changed();
		loadCatalog();
	}
	window.addEventListener('resize', () => { fit(); draw(); });
	start();
})();
