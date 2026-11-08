// 방 평면도 + 칸 결과를 SVG로 그리는 공통 함수 (햇빛 화면, 추천 화면에서 사용)
(function () {
	const LEVEL_COLOR = { BRIGHT: '#f5b400', MEDIUM: '#8cc084', DIM: '#5b6b7a' };

	function sunColor(minutes, max) {
		if (minutes <= 0) return 'transparent';
		const t = Math.min(1, minutes / Math.max(max, 60));
		return `rgba(245, 180, 0, ${0.15 + 0.75 * t})`;
	}

	function luxColor(lux) {
		const t = Math.max(0, Math.min(1, Math.log10(Math.max(lux, 50) / 50) / Math.log10(5000 / 50)));
		const l = 18 + 62 * t;
		return `hsl(140, 35%, ${l}%)`;
	}

	/**
	 * drawRoomPlan(svg, room, cells, {mode:'direct'|'level'|'lux', cellSize, onCell, marks:[{x,y,label,color}]})
	 */
	function drawRoomPlan(svg, room, cells, opt = {}) {
		const pts = room.shape;
		const xs = pts.map(p => p.x), ys = pts.map(p => p.y);
		const minX = Math.min(...xs), minY = Math.min(...ys);
		const w = Math.max(...xs) - minX, h = Math.max(...ys) - minY, pad = Math.max(w, h) * 0.1;
		svg.setAttribute('viewBox', `${minX - pad} ${minY - pad} ${w + pad * 2} ${h + pad * 2}`);
		const sw = Math.max(w, h) / 150, cs = opt.cellSize || 25;
		const maxDirect = Math.max(0, ...cells.map(c => c.directMinutes));
		let s = `<polygon points="${pts.map(p => p.x + ',' + p.y).join(' ')}" fill="var(--surface)" stroke="none"/>`;
		cells.forEach((c, i) => {
			let fill;
			if (opt.mode === 'level') fill = LEVEL_COLOR[c.level];
			else if (opt.mode === 'lux') fill = luxColor(c.lux);
			else fill = sunColor(c.directMinutes, maxDirect);
			const tip = `직사광 ${Math.floor(c.directMinutes / 60)}시간 ${c.directMinutes % 60}분 · 하늘빛 약 ${Math.round(c.lux)} lux · 창에서 ${Math.round(c.distance)}cm`;
			s += `<rect data-i="${i}" x="${c.x - cs / 2}" y="${c.y - cs / 2}" width="${cs}" height="${cs}" fill="${fill}" stroke="var(--line)" stroke-width="${sw / 4}"><title>${tip}</title></rect>`;
		});
		s += `<polygon points="${pts.map(p => p.x + ',' + p.y).join(' ')}" fill="none" stroke="var(--text)" stroke-width="${sw * 1.5}"/>`;
		(room.windows || []).forEach(win => {
			const a = pts[win.wallIndex], b = pts[(win.wallIndex + 1) % pts.length];
			const L = Math.hypot(b.x - a.x, b.y - a.y), ux = (b.x - a.x) / L, uy = (b.y - a.y) / L;
			const x1 = a.x + ux * win.offset, y1 = a.y + uy * win.offset;
			s += `<line x1="${x1}" y1="${y1}" x2="${x1 + ux * win.width}" y2="${y1 + uy * win.width}" stroke="#4aa3df" stroke-width="${sw * 4}"/>`;
		});
		(opt.marks || []).forEach(m => {
			s += `<circle cx="${m.x}" cy="${m.y}" r="${cs * 0.42}" fill="${m.color || 'var(--accent)'}" stroke="#fff" stroke-width="${sw}"><title>${escapeHtml(m.label || '')}</title></circle>`;
			if (m.text) s += `<text x="${m.x}" y="${m.y + cs * 0.15}" font-size="${cs * 0.45}" text-anchor="middle" fill="#fff">${escapeHtml(m.text)}</text>`;
		});
		const cx = minX - pad / 2, cy = minY - pad / 2;
		s += `<text x="${cx}" y="${cy}" font-size="${sw * 9}" fill="var(--accent)" text-anchor="middle" transform="rotate(${-room.orientationDeg} ${cx} ${cy})">N↑</text>`;
		svg.innerHTML = s;
		if (opt.onCell) {
			svg.onclick = (ev) => { const i = ev.target.dataset && ev.target.dataset.i; if (i !== undefined) opt.onCell(cells[Number(i)]); };
		}
	}

	window.drawRoomPlan = drawRoomPlan;
	window.LEVEL_COLOR = LEVEL_COLOR;
})();
