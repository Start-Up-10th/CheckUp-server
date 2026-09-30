package com.checkup.checkup.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 공통 오류 응답 본문. {@code errors}는 요청 검증 실패 때만 채우고, 비어 있으면 JSON에서 생략한다.
 *
 * @param code    {@link ErrorCode} 이름
 * @param message 오류 코드의 기본 메시지
 * @param errors  필드별 검증 오류
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors
) {
    /**
     * 필드 오류가 없는 오류 응답을 만든다.
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    /**
     * 요청 검증 실패처럼 필드별 오류를 함께 담는 오류 응답을 만든다.
     */
    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }

    /**
     * 검증에 실패한 필드 하나. 거부된 입력값은 민감정보일 수 있어 담지 않는다.
     *
     * @param field  요청 필드 이름
     * @param reason 검증 메시지
     */
    public record FieldError(String field, String reason) {}
}
