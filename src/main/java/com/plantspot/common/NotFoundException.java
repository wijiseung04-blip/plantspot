package com.plantspot.common;

/** 요청한 자원이 없을 때 던진다. GlobalExceptionHandler가 404로 바꾼다. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
