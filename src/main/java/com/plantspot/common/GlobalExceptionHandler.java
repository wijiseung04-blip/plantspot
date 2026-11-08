package com.plantspot.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

/** 공통 예외 처리: 모든 REST 오류를 ErrorResponse 형식으로 돌려준다. [필수: 공통 예외 처리, 로그] */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e, HttpServletRequest req) {
		log.info("자원 없음: {} ({})", e.getMessage(), req.getRequestURI());
		return build(HttpStatus.NOT_FOUND, e.getMessage(), req);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, message, req);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException e, HttpServletRequest req) {
		String message = e.getAllErrors().stream()
				.map(err -> err.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, message, req);
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
			IllegalArgumentException.class })
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, e.getMessage(), req);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
		log.error("처리하지 못한 오류 ({})", req.getRequestURI(), e);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", req);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
	}
}
