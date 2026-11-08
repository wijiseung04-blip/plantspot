package com.plantspot.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [① DB 데이터 생성 프로그램] 농사로 실내정원용 식물 목록 전체 → 상세 전부 수집 → plant_species 저장·변환.
 * 실시간 호출이 아니라 한 번에 모아 DB로 바꾼다. 호출 사이에 짧게 쉰다.
 */
@Service
public class PlantImportService {

	private static final Logger log = LoggerFactory.getLogger(PlantImportService.class);
	private static final int PAGE_SIZE = 50;

	private final NongsaroApi api;
	private final PlantSpeciesRepository repository;
	private final TransactionTemplate tx;
	private final long delayMillis;

	public PlantImportService(NongsaroApi api, PlantSpeciesRepository repository, TransactionTemplate tx,
			@Value("${api.nongsaro.delay-ms:300}") long delayMillis) {
		this.api = api;
		this.repository = repository;
		this.tx = tx;
		this.delayMillis = delayMillis;
	}

	public record Progress(int done, int total, String current) {
	}

	public record ImportResult(int total, int created, int updated, int failed, List<String> errors) {

		public String summary() {
			return String.format("식물 %d종 중 새로 %d, 갱신 %d, 실패 %d", total, created, updated, failed);
		}
	}

	public ImportResult importAll(Consumer<Progress> progress) {
		List<Map<String, String>> all = new ArrayList<>();
		int page = 1;
		int total;
		do {
			NongsaroXml.Page p = api.list(page, PAGE_SIZE);
			all.addAll(p.items());
			total = p.totalCount();
			page++;
			pause();
			if (p.items().isEmpty()) {
				break;
			}
		} while (all.size() < total);
		log.info("농사로 목록 {}건 (totalCount {})", all.size(), total);

		int created = 0;
		int updated = 0;
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			Map<String, String> item = all.get(i);
			String no = item.get("cntntsNo");
			progress.accept(new Progress(i, all.size(), item.get("cntntsSj")));
			try {
				NongsaroPlant plant = NongsaroXml.toPlant(item, api.detail(no));
				boolean isNew = Boolean.TRUE.equals(tx.execute(status -> {
					PlantSpecies entity = repository.findByCntntsNo(no).orElse(null);
					boolean fresh = entity == null;
					if (fresh) {
						entity = PlantSpecies.fromNongsaro(no);
					}
					entity.applyNongsaro(plant);
					repository.save(entity);
					return fresh;
				}));
				if (isNew) {
					created++;
				} else {
					updated++;
				}
			} catch (RuntimeException e) {
				errors.add(no + " " + item.get("cntntsSj") + ": " + e.getMessage());
				log.warn("농사로 상세 실패 {}: {}", no, e.getMessage());
			}
			pause();
		}
		progress.accept(new Progress(all.size(), all.size(), null));
		ImportResult r = new ImportResult(all.size(), created, updated, errors.size(), errors);
		log.info(r.summary());
		return r;
	}

	private void pause() {
		if (delayMillis <= 0) {
			return;
		}
		try {
			Thread.sleep(delayMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
