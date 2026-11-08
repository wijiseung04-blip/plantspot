package com.plantspot.common;

/** 남의 자원에 접근할 때 던진다. GlobalExceptionHandler가 403으로 바꾼다. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
