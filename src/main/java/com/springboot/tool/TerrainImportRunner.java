package com.springboot.tool;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.springboot.service.TerrainService;

/**
 * 명령줄 실행: ./gradlew importTerrain [① 데이터 생성 프로그램: 고도 API → terrain_grid·terrain_point]
 * 웹 서버 없이 가져오기만 하고 끝낸다. 건물 데이터를 먼저 가져와야 범위를 정할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "plantspot.import", havingValue = "terrain")
public class TerrainImportRunner implements CommandLineRunner {

	private final TerrainService service;
	private final ApplicationContext context;

	public TerrainImportRunner(TerrainService service, ApplicationContext context) {
		this.service = service;
		this.context = context;
	}

	@Override
	public void run(String... args) {
		int code = 0;
		try {
			TerrainService.ImportResult r = service.importAll(p -> {
				if (p.done() % 2000 < 100 || p.done() == p.total()) {
					System.out.printf("  %d/%d점%n", p.done(), p.total());
				}
			});
			System.out.println(r.summary());
			System.out.println("서버를 켜면 집마다 가림 각도를 다시 계산합니다 (산·경사 반영).");
		} catch (RuntimeException e) {
			System.out.println("지형 가져오기 실패: " + e.getMessage());
			System.out.println("받은 점은 저장되어 있으니 다시 실행하면 이어서 받습니다.");
			code = 1;
		}
		int exit = code;
		System.exit(SpringApplication.exit(context, () -> exit));
	}
}
