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
		let picked = null, parcel = [];

		/** 같은 지번의 건물 중 하나를 고르게 한다 (아파트 단지 등). */
		function showBuildings() {
			const label = (b) => [b.dongLabel || b.buildingName || '이름 없는 건물', b.groundFloors ? b.groundFloors + '층' : null,
				'높이 ' + b.height + 'm', b.usage, Math.round(b.area) + '㎡'].filter(Boolean).map(escapeHtml).join(' · ');
			// 처음 선택: 브이월드 동 이름(예: 101동)과 같은 건물 → 없으면 가장 높은 건물
			const detail = (picked.buildingDetail || '').replace(/\s/g, '');
			let def = parcel.find(b => detail && (b.dongLabel || '').replace(/\s/g, '') === detail)
				|| [...parcel].sort((a, b) => b.height - a.height || b.area - a.area)[0];
			out.innerHTML = `<div class="card" style="margin:6px 0;padding:10px">
				<p style="margin:0 0 6px"><strong>${escapeHtml(picked.roadAddress || picked.parcelAddress)}</strong></p>
				${parcel.length > 1 ? `<p class="muted" style="margin:0 0 6px">이 지번에 건물이 ${parcel.length}개 있습니다. 우리 집 건물(동)을 고르세요.</p>` : ''}
				${parcel.map(b => `<label style="display:block;margin:2px 0;color:var(--text)"><input type="radio" name="addr-bld" value="${b.id}" ${b.id === def.id ? 'checked' : ''}> ${label(b)}</label>`).join('')}
			</div>`;
			const emit = () => {
				const b = parcel.find(x => String(x.id) === out.querySelector('input[name=addr-bld]:checked').value);
				onPick({ buildingId: b.id, dong: b.dong, jibun: b.jibun, height: b.height, groundFloors: b.groundFloors,
					roadAddress: picked.roadAddress, parcelAddress: picked.parcelAddress });
			};
			out.querySelectorAll('input[name=addr-bld]').forEach(r => r.addEventListener('change', emit));
			emit();
		}

		out.addEventListener('click', async (ev) => {
			const i = ev.target.dataset.i;
			if (i == null) return;
			picked = results[Number(i)];
			try {
				parcel = await api(`/api/buildings?dong=${encodeURIComponent(picked.building.dong)}&jibun=${encodeURIComponent(picked.building.jibun)}`);
				showBuildings();
			} catch (e) { out.innerHTML = `<p class="flash err">${escapeHtml(e.message)}</p>`; }
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
