package com.plantspot.backend.plant;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/** 관리자 화면에서 누른 가져오기를 뒤에서 돌리고 진행 상황을 알려준다. 한 번에 하나만. */
@Component
public class PlantImportJob {

	private static final Logger log = LoggerFactory.getLogger(PlantImportJob.class);

	private final PlantImportService service;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "plant-import");
		t.setDaemon(true);
		return t;
	});
	private final AtomicReference<Status> status = new AtomicReference<>(Status.idle());

	public PlantImportJob(PlantImportService service) {
		this.service = service;
	}

	public record Status(boolean running, int done, int total, String current, LocalDateTime startedAt,
			LocalDateTime finishedAt, String result, String error) {

		static Status idle() {
			return new Status(false, 0, 0, null, null, null, null, null);
		}
	}

	public Status status() {
		return status.get();
	}

	/** 이미 돌고 있으면 false. */
	public synchronized boolean start() {
		if (status.get().running()) {
			return false;
		}
		LocalDateTime started = LocalDateTime.now();
		status.set(new Status(true, 0, 0, null, started, null, null, null));
		executor.submit(() -> {
			try {
				PlantImportService.ImportResult r = service.importAll(p -> status
						.set(new Status(true, p.done(), p.total(), p.current(), started, null, null, null)));
				status.set(new Status(false, r.total(), r.total(), null, started, LocalDateTime.now(), r.summary(),
						r.errors().isEmpty() ? null : String.join("\n", r.errors().subList(0, Math.min(5, r.errors().size())))));
			} catch (RuntimeException e) {
				log.error("농사로 가져오기 실패", e);
				Status s = status.get();
				status.set(new Status(false, s.done(), s.total(), null, started, LocalDateTime.now(), null, e.getMessage()));
			}
		});
		return true;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}
}
