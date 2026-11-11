// 화면에서 REST API를 부를 때 쓰는 공통 함수.
// /api/** 는 같은 서버의 REST 컨트롤러(com.springboot.controller)가 처리한다. 로그인은 세션 쿠키, 쓰기 요청은 CSRF 토큰 헤더가 필요하다.
(function () {
	let csrf = null; // { headerName, token }

	async function loadCsrf() {
		const res = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
		csrf = res.ok ? await res.json() : null;
	}

	/** api('/api/houses', {method:'POST', json:{...}}) → 응답 JSON. 오류면 message를 담은 Error를 던진다. */
	async function api(url, options = {}, retried = false) {
		const method = (options.method || 'GET').toUpperCase();
		const opts = { method, credentials: 'same-origin', headers: { ...(options.headers || {}) } };
		if (method !== 'GET' && method !== 'HEAD') {
			if (!csrf) await loadCsrf();
			if (csrf) opts.headers[csrf.headerName] = csrf.token;
		}
		if (options.json !== undefined) {
			opts.headers['Content-Type'] = 'application/json';
			opts.body = JSON.stringify(options.json);
		} else if (options.body !== undefined) {
			opts.body = options.body; // FormData 등
		}
		const res = await fetch(url, opts);
		// 로그인·로그아웃으로 CSRF 토큰이 바뀌었으면 한 번 다시 받아서 재시도
		if (res.status === 403 && !retried && method !== 'GET') {
			csrf = null;
			await loadCsrf();
			if (csrf) return api(url, options, true);
		}
		const text = await res.text();
		const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
		if (!res.ok) {
			const err = new Error((data && data.message) || res.statusText);
			err.status = res.status;
			throw err;
		}
		return data;
	}

	async function logout() {
		try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* 이미 로그아웃 */ }
		location.href = '/';
	}

	function escapeHtml(s) {
		return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
	}

	/**
	 * 동 안 세대 위치 선택칸 채우기. axis = 건물 긴 축 방향(0~180°, 서버 BuildingFacade.longAxis), +1 쪽이 axis 방향 끝.
	 * 긴 축을 모르면(외곽선 없음) 가운데만.
	 */
	function fillUnitPosition(select, axis, value) {
		const DIRS = ['북', '북동', '동', '남동', '남', '남서', '서', '북서'];
		const name = (deg) => DIRS[Math.round((((deg % 360) + 360) % 360) / 45) % 8];
		const v = value ?? 0;
		if (axis == null) {
			select.innerHTML = '<option value="0">가운데 (건물 모양 정보 없음)</option>';
			select.disabled = true;
			return;
		}
		select.disabled = false;
		const minus = name(axis + 180), plus = name(axis);
		const opts = [[-0.85, `${minus}쪽 끝`], [-0.45, `${minus}쪽`], [0, '가운데'], [0.45, `${plus}쪽`], [0.85, `${plus}쪽 끝`]];
		const nearest = opts.reduce((a, b) => Math.abs(b[0] - v) < Math.abs(a[0] - v) ? b : a)[0];
		select.innerHTML = opts.map(([p, l]) => `<option value="${p}" ${p === nearest ? 'selected' : ''}>${l}</option>`).join('');
	}

	window.api = api;
	window.fillUnitPosition = fillUnitPosition;
	window.logout = logout;
	window.escapeHtml = escapeHtml;
})();
