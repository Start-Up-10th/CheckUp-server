package com.checkup.checkup.global.exception;

import com.checkup.checkup.domain.face.ai.AiFaceException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /** Maps private AI failures to stable Spring errors; upstream details stay in sanitized server logs. */
    @ExceptionHandler(AiFaceException.class)
    public ResponseEntity<ErrorResponse> handleFaceAi(AiFaceException e) {
        log.warn("Face AI integration error: operation={}, status={}, errorCode={}",
                e.getOperation(), e.getStatus(), e.getErrorCode());
        ErrorCode code = switch (e.getStatus()) {
            case 400 -> ErrorCode.FACE_INVALID_MEDIA;
            case 413 -> ErrorCode.FACE_UPLOAD_TOO_LARGE;
            case 422 -> mapFaceAiUnprocessable(e);
            case 404 -> "frame".equals(e.getOperation())
                    ? ErrorCode.FACE_SESSION_NOT_FOUND : ErrorCode.FACE_AI_BAD_GATEWAY;
            case 401, 500, 503 -> ErrorCode.FACE_AI_UNAVAILABLE;
            case 504 -> ErrorCode.FACE_AI_TIMEOUT;
            default -> ErrorCode.FACE_AI_BAD_GATEWAY;
        };
        return toResponse(code);
    }

    private static ErrorCode mapFaceAiUnprocessable(AiFaceException e) {
        if ("MODEL_MISMATCH".equals(e.getErrorCode())) {
            return ErrorCode.FACE_AI_BAD_GATEWAY;
        }
        if ("MULTIPLE_IDENTITIES".equals(e.getErrorCode()) && "enrollment".equals(e.getOperation())) {
            return ErrorCode.FACE_ENROLLMENT_MULTIPLE_IDENTITIES;
        }
        if ("LOW_LIGHT".equals(e.getErrorCode()) && "enrollment".equals(e.getOperation())) {
            return ErrorCode.FACE_ENROLLMENT_LOW_LIGHT;
        }
        return switch (e.getOperation()) {
            case "enrollment" -> ErrorCode.FACE_ENROLLMENT_REJECTED;
            case "frame" -> ErrorCode.FACE_INVALID_FRAME;
            default -> ErrorCode.FACE_AI_BAD_GATEWAY;
        };
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.FACE_UPLOAD_TOO_LARGE);
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
