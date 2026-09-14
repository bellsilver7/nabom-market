package com.example.nabom_market.common.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.nabom_market.common.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리기.
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속하면 헤더 누락, 타입 불일치, 본문 파싱 실패 같은
 * 표준 MVC 예외가 올바른 4xx로 매핑된다. 부모가 이미 다루는 예외는 {@code @ExceptionHandler}로
 * 다시 등록하면 "Ambiguous @ExceptionHandler" 로 기동이 실패하므로 반드시 {@code @Override}를 쓴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[{}]: {}", errorCode.name(), e.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    /** 검증 실패. 부모가 이미 맡고 있으므로 재정의해서 메시지 형식만 바꾼다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message));
    }

    /** 나머지 표준 MVC 예외의 응답 본문을 우리 포맷으로 통일한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        ErrorCode errorCode = status.is4xxClientError()
                ? ErrorCode.INVALID_REQUEST
                : ErrorCode.INTERNAL_ERROR;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    /** 어느 핸들러도 맡지 않은, 예상 밖의 예외. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()));
    }
}
