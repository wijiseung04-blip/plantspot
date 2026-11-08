package com.plantspot.common;

/** 이미 있는 값(중복 아이디 등)과 부딪칠 때 던진다. GlobalExceptionHandler가 409로 바꾼다. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
