package com.plantspot.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 이미 있는 값(중복 아이디 등)과 부딪칠 때 던진다. GlobalExceptionHandler가 409로 바꾼다. */
@ResponseStatus(HttpStatus.CONFLICT) // 화면(Thymeleaf) 요청에서는 오류 화면 상태 코드로 쓰임
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
