// 도로명주소(또는 지번주소)로 우리 건물 찾기 — 집 등록·간단 예측 화면에서 함께 쓴다.
// addressSearch(box, onPick): box 안에 검색칸과 결과 목록을 만들고, 건물을 고르면 onPick({dong, jibun, ...}) 호출
// parcelLookup(동 선택칸, 지번 칸, 목록 칸, onPick): 동·지번을 직접 입력해도 같은 건물 고르기 목록을 보여 준다
(function () {
	function addressSearch(box, onPick) {
		box.innerHTML = `
			<div class="row" style="align-items:end">
				<label style="flex:3 1 260px">도로명주소로 찾기 <input type="search" class="addr-q" placeholder="예: 낙동대로 550, 하단동 1160-6"></label>
				<div style="flex:0 0 auto"><button type="button" class="addr-go secondary">주소 검색</button></div>
			</div>
			<div class="addr-results"></div>`;
		const q = box.querySelector('.addr-q'), out = box.querySelector('.addr-results');
		let results = [];

		async function run() {
			out.innerHTML = '<p class="muted">찾는 중…</p>';
			try {
				results = await api('/api/address/search?query=' + encodeURIComponent(q.value.trim()));
				out.innerHTML = results.length ? results.map((r, i) => `
					<div class="card" style="margin:6px 0;padding:10px;display:flex;gap:10px;align-items:center;justify-content:space-between">
						<div>
							<strong>${escapeHtml(r.roadAddress || r.parcelAddress || '')}</strong>
							${r.buildingName ? ` <span class="muted">${escapeHtml(r.buildingName)}${r.buildingDetail ? ' ' + escapeHtml(r.buildingDetail) : ''}</span>` : ''}
							<div class="muted">${r.parcelAddress ? '지번 ' + escapeHtml(r.parcelAddress) : ''}${r.zipcode ? ' · 우편 ' + escapeHtml(r.zipcode) : ''}</div>
							<div class="muted">${r.building ? `건물 데이터: ${escapeHtml(r.building.dong)} ${escapeHtml(r.building.jibun)}`
								: '⚠ 우리 건물 데이터(부산 사하구)에 없는 주소입니다.'}</div>
						</div>
						<button type="button" class="small" data-i="${i}" ${r.building ? '' : 'disabled'}>이 건물</button>
					</div>`).join('') : '<p class="muted">검색 결과가 없습니다. 도로명과 건물번호를 확인하세요.</p>';
			} catch (e) {
				out.innerHTML = `<p class="flash err">${escapeHtml(e.message)}</p>`;
			}
		}
		box.querySelector('.addr-go').addEventListener('click', run);
		q.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') { ev.preventDefault(); run(); } });
		let picked = null;
		out.addEventListener('click', async (ev) => {
			const i = ev.target.dataset.i;
			if (i == null) return;
			picked = results[Number(i)];
			try {
				const parcel = await api(`/api/buildings?dong=${encodeURIComponent(picked.building.dong)}&jibun=${encodeURIComponent(picked.building.jibun)}`);
				buildingChooser(out, parcel, {
					title: picked.roadAddress || picked.parcelAddress, detail: picked.buildingDetail,
					extra: { roadAddress: picked.roadAddress, parcelAddress: picked.parcelAddress }
				}, onPick);
			} catch (e) { out.innerHTML = `<p class="flash err">${escapeHtml(e.message)}</p>`; }
		});
	}

	/**
	 * 같은 지번의 건물 중 하나를 고르게 한다 (아파트 단지 등). 주소 검색과 동·지번 직접 입력이 함께 쓴다.
	 * opts.title: 맨 위 굵은 글씨, opts.detail: 브이월드 동 이름(예: 101동, 처음 선택에 씀), opts.extra: onPick 에 더할 값
	 */
	function buildingChooser(out, parcel, opts, onPick) {
		// 부속 시설은 접어 둔다: 이름·층수가 없는 건물(건축물대장 미연계)
		// 또는 동 번호가 있어도 1층 이하·50㎡ 미만인 작은 건물(경비실·분리수거장·주차장 출입구 등)
		const isAnnex = (b) => (!b.dongLabel && !b.buildingName && !b.groundFloors)
			|| ((b.groundFloors || 0) <= 1 && b.area < 50);
		// 동 번호 순(101동, 102동 … → 번호 없는 이름 가나다순 → 이름 없는 건물은 큰 것부터)
		const dongNo = (b) => { const m = (b.dongLabel || '').match(/\d+/); return m ? Number(m[0]) : Infinity; };
		const name = (b) => b.dongLabel || b.buildingName || '';
		const byDong = (a, b) => dongNo(a) - dongNo(b) || (!name(a) - !name(b))
			|| name(a).localeCompare(name(b), 'ko', { numeric: true }) || b.area - a.area;
		const label = (b) => [b.dongLabel || b.buildingName || '부속 건물(정보 없음)', b.groundFloors ? b.groundFloors + '층' : null,
			'높이 ' + b.height + 'm', b.usage, Math.round(b.area) + '㎡'].filter(Boolean).map(escapeHtml).join(' · ');
		// 처음 선택: 브이월드 동 이름(예: 101동)과 같은 건물 → 없으면 가장 높은 건물
		const detail = (opts.detail || '').replace(/\s/g, '');
		const def = parcel.find(b => detail && (b.dongLabel || '').replace(/\s/g, '') === detail)
			|| [...parcel].sort((a, b) => b.height - a.height || b.area - a.area)[0];
		// 모두 부속 건물이면(단독주택 등) 접지 않고 다 보여준다
		const sorted = [...parcel].sort(byDong);
		const main = sorted.every(isAnnex) ? sorted : sorted.filter(b => !isAnnex(b) || b.id === def.id);
		const annex = sorted.filter(b => !main.includes(b));
		const group = 'bld-' + Math.random().toString(36).slice(2, 8);
		const radio = (b) => `<label style="display:block;margin:2px 0;color:var(--text)"><input type="radio" name="${group}" value="${b.id}" ${b.id === def.id ? 'checked' : ''}> ${label(b)}</label>`;
		out.innerHTML = `<div class="card" style="margin:6px 0;padding:10px">
			<p style="margin:0 0 6px"><strong>${escapeHtml(opts.title || '')}</strong></p>
			${parcel.length > 1 ? `<p class="muted" style="margin:0 0 6px">이 지번에 건물이 ${main.length}개 있습니다. 우리 집 건물(동)을 고르세요.</p>` : ''}
			${main.map(radio).join('')}
			${annex.length ? `<details style="margin-top:6px"><summary class="muted" style="cursor:pointer">부속 시설 ${annex.length}개 더 보기 (경비실·분리수거장·주차장 출입구 등 1층 이하 작은 건물)</summary>
				${annex.map(radio).join('')}</details>` : ''}
		</div>`;
		const emit = () => {
			const b = parcel.find(x => String(x.id) === out.querySelector(`input[name=${group}]:checked`).value);
			onPick({ buildingId: b.id, dong: b.dong, jibun: b.jibun, height: b.height, groundFloors: b.groundFloors, axis: b.axis,
				...(opts.extra || {}) });
		};
		out.querySelectorAll(`input[name=${group}]`).forEach(r => r.addEventListener('change', emit));
		emit();
	}

	/**
	 * 동 선택 + 지번 직접 입력: 지번 칸을 벗어나거나 Enter, 동을 바꾸면 그 지번의 건물 목록을 불러와 고르게 한다.
	 * (주소 검색 키가 없을 때도 아파트 동·동 안 위치를 고를 수 있게)
	 */
	function parcelLookup(dongSelect, jibunInput, out, onPick) {
		let last = '';
		async function run() {
			const dong = dongSelect.value, jibun = jibunInput.value.trim();
			if (!dong || !jibun) { out.innerHTML = ''; last = ''; return; }
			if (dong + '|' + jibun === last) return;
			last = dong + '|' + jibun;
			out.innerHTML = '<p class="muted">건물 찾는 중…</p>';
			try {
				const parcel = await api(`/api/buildings?dong=${encodeURIComponent(dong)}&jibun=${encodeURIComponent(jibun)}`);
				if (!parcel.length) {
					out.innerHTML = `<p class="flash err">${escapeHtml(dong)} ${escapeHtml(jibun)} 에 건물 데이터가 없습니다. 지번을 확인하세요.</p>`;
					return;
				}
				buildingChooser(out, parcel, { title: `${dong} ${jibun}` }, onPick);
			} catch (e) { out.innerHTML = `<p class="flash err">${escapeHtml(e.message)}</p>`; last = ''; }
		}
		jibunInput.addEventListener('change', run);
		jibunInput.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') { ev.preventDefault(); run(); } });
		dongSelect.addEventListener('change', run);
		return {
			run,
			/** 주소 검색으로 고른 뒤에는 직접 입력 목록을 비운다 */
			clear() { out.innerHTML = ''; last = dongSelect.value + '|' + jibunInput.value.trim(); }
		};
	}

	/** 동 선택칸에 그 동이 없으면 추가하고 고른다. */
	function selectDong(select, dong) {
		if (![...select.options].some(o => o.value === dong)) select.add(new Option(dong, dong));
		select.value = dong;
	}

	window.addressSearch = addressSearch;
	window.buildingChooser = buildingChooser;
	window.parcelLookup = parcelLookup;
	window.selectDong = selectDong;
})();
