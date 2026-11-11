package com.springboot.tool;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.springboot.service.TerrainService;

/**
 * 명령줄 실행: ./gradlew importTerrain -Pdem=C:/data/saha_dem.asc
 * [① 데이터 생성 프로그램: 수치표고모델(DEM) 파일 → terrain_grid·terrain_point]
 * 웹 서버 없이 가져오기만 하고 끝낸다. 건물 데이터를 먼저 가져와야 범위를 정할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "plantspot.import", havingValue = "terrain")
public class TerrainImportRunner implements CommandLineRunner {

	private final TerrainService service;
	private final ApplicationContext context;
	private final String dem;

	public TerrainImportRunner(TerrainService service, ApplicationContext context, @Value("${dem:}") String dem) {
		this.service = service;
		this.context = context;
		this.dem = dem;
	}

	@Override
	public void run(String... args) {
		int code = 0;
		if (dem.isBlank()) {
			System.out.println("사용법: ./gradlew importTerrain -Pdem=<ASCII Grid(.asc) 경로>  (또는 gradle.properties 에 dem=경로)");
			code = 1;
		} else {
			try {
				TerrainService.ImportResult r = service.importFile(Path.of(dem), System.out::println);
				System.out.println(r.summary());
				System.out.println("서버를 켜면 집마다 가림 각도를 다시 계산합니다 (산·경사 반영).");
			} catch (RuntimeException e) {
				System.out.println("지형 가져오기 실패: " + e.getMessage());
				code = 1;
			}
		}
		int exit = code;
		System.exit(SpringApplication.exit(context, () -> exit));
	}
}
