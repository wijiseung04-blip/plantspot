package com.plantspot.common;

import java.time.LocalDateTime;

/** REST API 공통 오류 응답 형식. */
public record ErrorResponse(int status, String error, String message, String path, LocalDateTime timestamp) {

	public static ErrorResponse of(int status, String error, String message, String path) {
		return new ErrorResponse(status, error, message, path, LocalDateTime.now());
	}
}
