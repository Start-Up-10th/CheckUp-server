package com.checkup.checkup.global.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;

/**
 * 예외를 {@link ErrorResponse}로 변환한다. 응답과 로그에는 예외 메시지 대신 {@link ErrorCode}만 쓴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스 예외는 담긴 {@link ErrorCode}로 응답한다. */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException e) {
        return toResponse(e.getErrorCode());
    }

    /** {@code @Valid} 실패. MethodArgumentNotValidException도 BindException 하위라 함께 처리된다. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, errors));
    }

    /** 필수 파라미터 누락, 타입 불일치, 읽을 수 없는 body(잘못된 JSON·enum 값)는 400으로 응답한다. */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    /** 없는 경로는 404로 응답한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND);
    }

    /** 지원하지 않는 HTTP 메서드는 405로 응답한다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** DataGSM 401·403은 서버 설정 문제라 클라이언트 인증 실패로 돌려주지 않는다. */
    @ExceptionHandler(DataGsmException.class)
    public ResponseEntity<ErrorResponse> handleDataGsm(DataGsmException e) {
        ErrorCode errorCode = DataGsmErrorCodes.of(e);
        // SDK 메시지에 외부 응답이 섞일 수 있어 클래스 이름만 남긴다.
        log.warn("DataGSM request failed: {}", e.getClass().getSimpleName());
        return toResponse(errorCode);
    }

    /**
     * 위에서 처리하지 못한 예외.
     *
     * Spring MVC 기본 예외(415·406 등)와 {@link ResponseStatusException}은 Spring의
     * {@link org.springframework.web.ErrorResponse}를 구현하므로 원래 상태 코드를 유지한다.
     * 그 밖의 예외는 스택을 로그에만 남기고 500으로 응답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            if (status.is5xxServerError()) {
                log.error("Unhandled exception", e);
            }
            return ResponseEntity.status(status).body(ErrorResponse.of(errorCodeOf(status)));
        }
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** 상태 코드에 맞는 공통 {@link ErrorCode}. 따로 없는 4xx는 {@code INVALID_REQUEST}로 본다. */
    private static ErrorCode errorCodeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> status.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
