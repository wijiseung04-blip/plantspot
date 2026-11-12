// 평면도 햇빛 에디터(브라우저 JS)와 서버(Java) 계산이 같은지 확인한다.
// 실행: node src/test/js/editor-parity.js
// 기대값은 SunlightCalculatorTest 와 같은 방(4m × 3m, 남쪽 창 200 × 150cm, 창턱 60cm)을 서버로 계산한 값.
global.window = global;
const path = require('path');
const dir = path.join(__dirname, '../../main/resources/static/editor');
require(path.join(dir, 'solar.js'));
require(path.join(dir, 'light.js'));

let failed = 0;
function check(name, actual, expected, tol) {
	const ok = Math.abs(actual - expected) <= tol;
	if (!ok) failed++;
	console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}: ${actual} (기대 ${expected} ± ${tol})`);
}

const lat = 35.10, lon = 128.97;
[['2026-03-20', 54.7], ['2026-06-21', 78.3], ['2026-12-21', 31.5]].forEach(([d, exp]) => {
	const t = Solar.sunTimes(d, lat, lon);
	check(`${d} 정오 고도`, Solar.position(Solar.koreaMs(d, t.solarNoon), lat, lon).altitude.toFixed(2), exp, 0.2);
});
check('2026-11-08 일출(분)', Math.round(Solar.sunTimes('2026-11-08', lat, lon).sunrise), 6 * 60 + 52, 2);

const room = { points: [{ x: 0, y: 0 }, { x: 400, y: 0 }, { x: 400, y: 300 }, { x: 0, y: 300 }], orientationDeg: 0,
	ceilingHeight: 230, curtainT: 1, windows: [{ wallIndex: 2, offset: 100, width: 200, height: 150, sill: 60 }] };
const server = {
	'2026-03-20': [[12.5, 813, 0], [62.5, 1846, 345], [112.5, 1393, 210], [287.5, 385, 0]],
	'2026-12-22': [[12.5, 631, 0], [62.5, 1433, 0], [112.5, 1082, 240], [287.5, 299, 135]]
};
for (const [date, rows] of Object.entries(server)) {
	const cells = Light.dayAt(room, rows.map(r => [187.5, 300 - r[0]]), date, 35.1154, 128.9605, null);
	rows.forEach((r, i) => {
		check(`${date} 창에서 ${r[0]}cm 하늘빛 lux`, Math.round(cells[i].lux), r[1], 2);
		check(`${date} 창에서 ${r[0]}cm 직사광(분)`, cells[i].directMinutes, r[2], 0);
	});
}
// 안쪽 창 선 (남쪽 벽에서 150cm, 유리 0.75): 발코니 칸은 그대로, 거실 칸은 0.75배 — 서버 SunlightCalculator.innerGlassFactor 와 같음
{
	const inner = { ...room, innerGlass: { wallIndex: 2, distance: 150, transmittance: 0.75 } };
	const rows = server['2026-12-22'];
	const cells = Light.dayAt(inner, rows.map(r => [187.5, 300 - r[0]]), '2026-12-22', 35.1154, 128.9605, null);
	rows.forEach((r, i) => {
		const expected = r[0] > 150 ? r[1] * 0.75 : r[1];
		check(`안쪽 창 · 창에서 ${r[0]}cm 하늘빛 lux`, Math.round(cells[i].lux), Math.round(expected), 2);
		check(`안쪽 창 · 창에서 ${r[0]}cm 직사광(분)`, cells[i].directMinutes, r[2], 0);
	});
}
// 식물 점수 — 서버 PlantScorerTest 와 같은 값 (브라우저는 정수로 반올림)
{
	const mid = { minLux: 800, maxLux: 1500, minDirectMinutes: 0, maxDirectMinutes: 180, minWindowDistance: null, maxWindowDistance: null };
	[[1095, 100], [1000, 98], [950, 96], [1250, 97], [800, 80]].forEach(([lux, exp]) =>
		check(`식물 점수 800~1500 lux · ${lux} lux`, Light.plantScore(mid, { lux, directMinutes: 0, distance: 100 }), exp, 0));
}
if (failed) {
	console.error(`${failed}개 불일치`);
	process.exit(1);
}
console.log('서버와 브라우저 계산 일치');
