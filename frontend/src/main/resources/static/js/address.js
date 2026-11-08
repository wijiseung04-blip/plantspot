// 도로명주소(또는 지번주소)로 우리 건물 찾기 — 집 등록·간단 예측 화면에서 함께 쓴다.
// addressSearch(box, onPick): box 안에 검색칸과 결과 목록을 만들고, 건물을 고르면 onPick({dong, jibun, ...}) 호출
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
							${r.buildingName ? ` <span class="muted">${escapeHtml(r.buildingName)}</span>` : ''}
							<div class="muted">${r.parcelAddress ? '지번 ' + escapeHtml(r.parcelAddress) : ''}${r.zipcode ? ' · 우편 ' + escapeHtml(r.zipcode) : ''}</div>
							<div class="muted">${r.building ? `건물 데이터: ${escapeHtml(r.building.dong)} ${escapeHtml(r.building.jibun)} · 높이 ${r.building.height}m${r.building.groundFloors ? ' · ' + r.building.groundFloors + '층' : ''}`
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
		out.addEventListener('click', (ev) => {
			const i = ev.target.dataset.i;
			if (i == null) return;
			const r = results[Number(i)];
			out.innerHTML = `<p class="flash ok">선택: ${escapeHtml(r.roadAddress || r.parcelAddress)} → ${escapeHtml(r.building.dong)} ${escapeHtml(r.building.jibun)}</p>`;
			onPick({ ...r.building, roadAddress: r.roadAddress, parcelAddress: r.parcelAddress });
		});
	}

	/** 동 선택칸에 그 동이 없으면 추가하고 고른다. */
	function selectDong(select, dong) {
		if (![...select.options].some(o => o.value === dong)) select.add(new Option(dong, dong));
		select.value = dong;
	}

	window.addressSearch = addressSearch;
	window.selectDong = selectDong;
})();
