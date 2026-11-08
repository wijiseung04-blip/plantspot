package com.plantspot.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 남의 자원에 접근할 때 던진다. GlobalExceptionHandler가 403으로 바꾼다. */
@ResponseStatus(HttpStatus.FORBIDDEN) // 화면(Thymeleaf) 요청에서는 오류 화면 상태 코드로 쓰임
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
