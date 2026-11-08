package com.plantspot.front;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * plantspot 화면 서버 (Thymeleaf). DB·로그인 판단은 backend가 하고, 이 서버는 화면을 그리고
 * 브라우저의 /api/** 요청을 backend로 그대로 전달한다.
 */
@SpringBootApplication
public class PlantspotFrontendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlantspotFrontendApplication.class, args);
	}
}
