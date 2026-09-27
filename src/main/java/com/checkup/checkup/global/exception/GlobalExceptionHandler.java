package com.checkup.checkup.global.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.exception.RateLimitException;
import team.themoment.datagsm.sdk.oauth.exception.ServerErrorException;

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

    /**
     * 아직 {@link CustomException}으로 바꾸지 않은 코드의 {@link ResponseStatusException}을 처리한다.
     * 이 핸들러가 없으면 아래 {@code Exception} 핸들러가 잡아 모두 500이 된다.
     * 상태 코드는 유지하고, 코드·메시지는 같은 상태의 공통 {@link ErrorCode}를 쓴다.
     *
     * <p>임시 처리다. 모든 {@code ResponseStatusException}을 교체하면 삭제한다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        ErrorCode errorCode = switch (e.getStatusCode().value()) {
            case 400 -> ErrorCode.INVALID_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(e.getStatusCode()).body(ErrorResponse.of(errorCode));
    }

    /** 필수 파라미터 누락과 타입 불일치는 400으로 응답한다. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
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
        ErrorCode errorCode = switch (e) {
            case BadRequestException ignored -> ErrorCode.DATAGSM_INVALID_CODE;
            case ServerErrorException ignored -> ErrorCode.DATAGSM_UNAVAILABLE;
            case RateLimitException ignored -> ErrorCode.DATAGSM_UNAVAILABLE;
            default -> ErrorCode.DATAGSM_ERROR;
        };
        // SDK 메시지에 외부 응답이 섞일 수 있어 클래스 이름만 남긴다.
        log.warn("DataGSM request failed: {}", e.getClass().getSimpleName());
        return toResponse(errorCode);
    }

    /** 처리하지 못한 예외는 스택을 로그에만 남기고 500으로 응답한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
