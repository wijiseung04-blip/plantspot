package com.plantspot.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 요청한 자원이 없을 때 던진다. GlobalExceptionHandler가 404로 바꾼다. */
@ResponseStatus(HttpStatus.NOT_FOUND) // 화면(Thymeleaf) 요청에서는 오류 화면 상태 코드로 쓰임
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
