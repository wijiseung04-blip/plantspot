package com.springboot.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST API 공통 예외 처리: 모든 오류를 ErrorResponse 형식(JSON)으로 돌려준다. [필수: 공통 예외 처리, 로그]
 * 화면(Thymeleaf) 컨트롤러 오류는 templates/error.html 이 처리한다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e, HttpServletRequest req) {
		log.info("자원 없음: {} ({})", e.getMessage(), req.getRequestURI());
		return build(HttpStatus.NOT_FOUND, e.getMessage(), req);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e, HttpServletRequest req) {
		log.warn("권한 없음: {} ({})", e.getMessage(), req.getRequestURI());
		return build(HttpStatus.FORBIDDEN, e.getMessage(), req);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException e, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, e.getMessage(), req);
	}

	@ExceptionHandler(UnavailableException.class)
	public ResponseEntity<ErrorResponse> handleUnavailable(UnavailableException e, HttpServletRequest req) {
		log.warn("처리 불가: {} ({})", e.getMessage(), req.getRequestURI());
		return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), req);
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

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e,
			HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "필수 값 '" + e.getParameterName() + "'이(가) 없습니다.", req);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e,
			HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "필수 파일 '" + e.getRequestPartName() + "'이(가) 없습니다.", req);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
			HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "'" + e.getName() + "' 값의 형식이 잘못되었습니다: " + e.getValue(), req);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "요청 본문(JSON)을 읽을 수 없습니다.", req);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, e.getMethod() + " 요청은 지원하지 않습니다.", req);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e, HttpServletRequest req) {
		return build(HttpStatus.CONTENT_TOO_LARGE, "파일이 너무 큽니다.", req);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, e.getMessage(), req);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) throws Exception {
		if (e instanceof org.springframework.security.access.AccessDeniedException
				|| e instanceof org.springframework.security.core.AuthenticationException) {
			throw e; // Spring Security가 401/403으로 처리
		}
		log.error("처리하지 못한 오류 ({})", req.getRequestURI(), e);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", req);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
	}
}
