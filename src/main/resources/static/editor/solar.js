// 해 위치 계산 (NOAA 공식) — 서버 SolarCalculator.java 와 같은 공식. [② 브라우저 프로그램]
(function (global) {
	const rad = Math.PI / 180, deg = 180 / Math.PI;
	const mod = (v, m) => ((v % m) + m) % m;

	function terms(jd) {
		const t = (jd - 2451545.0) / 36525.0;
		const L0 = mod(280.46646 + t * (36000.76983 + t * 0.0003032), 360);
		const M = 357.52911 + t * (35999.05029 - 0.0001537 * t);
		const e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
		const m = M * rad;
		const C = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + Math.sin(2 * m) * (0.019993 - 0.000101 * t) + Math.sin(3 * m) * 0.000289;
		const omega = (125.04 - 1934.136 * t) * rad;
		const lambda = L0 + C - 0.00569 - 0.00478 * Math.sin(omega);
		const eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
		const eps = (eps0 + 0.00256 * Math.cos(omega)) * rad;
		const decl = Math.asin(Math.sin(eps) * Math.sin(lambda * rad)) * deg;
		const y = Math.tan(eps / 2) ** 2, l0 = L0 * rad;
		const eot = 4 * deg * (y * Math.sin(2 * l0) - 2 * e * Math.sin(m) + 4 * e * y * Math.sin(m) * Math.cos(2 * l0)
			- 0.5 * y * y * Math.sin(4 * l0) - 1.25 * e * e * Math.sin(2 * m));
		return { eot, decl };
	}

	const julian = (ms) => ms / 86400000 + 2440587.5;

	/** ms(UTC 기준 epoch 밀리초), 위도, 경도 → {altitude, azimuth} (도, 방위각 북0/동90/남180/서270) */
	function position(ms, lat, lon) {
		const { eot, decl } = terms(julian(ms));
		const utcMin = mod(ms / 60000, 1440);
		const tst = mod(utcMin + eot + 4 * lon, 1440);
		const ha = (tst / 4 - 180) * rad, la = lat * rad, d = decl * rad;
		const cosZ = Math.max(-1, Math.min(1, Math.sin(la) * Math.sin(d) + Math.cos(la) * Math.cos(d) * Math.cos(ha)));
		const altitude = 90 - Math.acos(cosZ) * deg;
		const az = Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(la) - Math.tan(d) * Math.cos(la)) * deg;
		return { altitude, azimuth: mod(az + 180, 360) };
	}

	/** 한국 시각(UTC+9) 날짜 'YYYY-MM-DD' + 분 → epoch ms */
	function koreaMs(dateStr, minutes) {
		const [y, mo, d] = dateStr.split('-').map(Number);
		return Date.UTC(y, mo - 1, d, 0, 0) - 9 * 3600000 + minutes * 60000;
	}

	/** 일출·남중·일몰 (한국 시각 분, 없으면 null) */
	function sunTimes(dateStr, lat, lon) {
		const [y, mo, d] = dateStr.split('-').map(Number);
		const utc0 = Date.UTC(y, mo - 1, d);
		let noon = 720 - 4 * lon;
		for (let i = 0; i < 2; i++) noon = 720 - 4 * lon - terms(julian(utc0 + noon * 60000)).eot;
		const rs = (rise) => {
			let m = 720 - 4 * lon;
			for (let i = 0; i < 3; i++) {
				const { eot, decl } = terms(julian(utc0 + m * 60000));
				const cosH = Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl * rad)) - Math.tan(lat * rad) * Math.tan(decl * rad);
				if (cosH < -1 || cosH > 1) return null;
				const H = Math.acos(cosH) * deg * (rise ? 1 : -1);
				m = 720 - 4 * (lon + H) - eot;
			}
			return m;
		};
		const toKst = (m) => m == null ? null : mod(m + 540, 1440);
		return { sunrise: toKst(rs(true)), solarNoon: toKst(noon), sunset: toKst(rs(false)) };
	}

	global.Solar = { position, koreaMs, sunTimes };
})(window);
