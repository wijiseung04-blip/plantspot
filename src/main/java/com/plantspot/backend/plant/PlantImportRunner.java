package com.plantspot.backend.plant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 명령줄 실행: ./gradlew importPlants (웹 서버 없이 가져오기만 하고 끝낸다).
 */
@Component
@ConditionalOnProperty(name = "plantspot.import", havingValue = "plants")
public class PlantImportRunner implements CommandLineRunner {

	private final PlantImportService service;
	private final ApplicationContext context;

	public PlantImportRunner(PlantImportService service, ApplicationContext context) {
		this.service = service;
		this.context = context;
	}

	@Override
	public void run(String... args) {
		PlantImportService.ImportResult r = service.importAll(p -> {
			if (p.current() != null && p.done() % 10 == 0) {
				System.out.printf("  %d/%d %s%n", p.done(), p.total(), p.current());
			}
		});
		System.out.println(r.summary());
		r.errors().forEach(e -> System.out.println("  실패: " + e));
		System.exit(SpringApplication.exit(context, () -> r.failed() == 0 ? 0 : 1));
	}
}
