package com.plantspot.backend.weather;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.plantspot.backend.sun.SunService;

/** 명령줄: ./gradlew collectWeather -Pfrom=2026-11-01 [-Pto=2026-11-07] (ASOS 저장 후 종료) */
@Component
@ConditionalOnProperty(name = "plantspot.import", havingValue = "weather")
public class WeatherCollectRunner implements CommandLineRunner {

	private final PublicDataService service;
	private final ApplicationContext context;
	private final String from;
	private final String to;

	public WeatherCollectRunner(PublicDataService service, ApplicationContext context,
			@Value("${from:}") String from, @Value("${to:}") String to) {
		this.service = service;
		this.context = context;
		this.from = from;
		this.to = to;
	}

	@Override
	public void run(String... args) {
		LocalDate yesterday = LocalDate.now(SunService.SEOUL).minusDays(1);
		LocalDate f = from.isBlank() ? yesterday.minusDays(6) : LocalDate.parse(from);
		LocalDate t = to.isBlank() ? yesterday : LocalDate.parse(to);
		var r = service.collectWeather(f, t, service.defaultStation());
		System.out.printf("ASOS 지점 %d: %s~%s %d일 조회, %d시간 저장, %d일은 이미 있음%n", r.stationId(), r.from(), r.to(),
				r.daysFetched(), r.hoursSaved(), r.daysSkipped());
		System.exit(SpringApplication.exit(context, () -> 0));
	}
}
