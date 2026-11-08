package com.plantspot.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 서버 환경 때문에 지금 처리할 수 없을 때 (예: 한글 글꼴 없음). 503. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class UnavailableException extends RuntimeException {

	public UnavailableException(String message) {
		super(message);
	}
}
