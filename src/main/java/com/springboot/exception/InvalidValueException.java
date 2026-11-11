package com.springboot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 형식은 맞지만 다른 데이터와 맞지 않는 값(예: 건물 층수보다 높은 층). Bean Validation 으로는 알 수 없어 서비스에서 던진다.
 * GlobalExceptionHandler 가 400 으로 바꾼다.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidValueException extends RuntimeException {

	private final String field;

	public InvalidValueException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}
}
