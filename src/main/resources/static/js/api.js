// 화면에서 REST API를 부를 때 쓰는 공통 함수. CSRF 토큰을 헤더에 자동으로 붙인다.
(function () {
	function csrf() {
		const token = document.querySelector('meta[name="_csrf"]');
		const header = document.querySelector('meta[name="_csrf_header"]');
		return token && header ? { [header.content]: token.content } : {};
	}

	/** api('/api/houses', {method:'POST', json:{...}}) → 응답 JSON. 오류면 message를 담은 Error를 던진다. */
	async function api(url, options = {}) {
		const opts = { method: options.method || 'GET', headers: { ...csrf(), ...(options.headers || {}) } };
		if (options.json !== undefined) {
			opts.headers['Content-Type'] = 'application/json';
			opts.body = JSON.stringify(options.json);
		} else if (options.body !== undefined) {
			opts.body = options.body; // FormData 등
		}
		const res = await fetch(url, opts);
		const text = await res.text();
		const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
		if (!res.ok) {
			const err = new Error((data && data.message) || res.statusText);
			err.status = res.status;
			throw err;
		}
		return data;
	}

	function escapeHtml(s) {
		return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
	}

	window.api = api;
	window.escapeHtml = escapeHtml;
})();
