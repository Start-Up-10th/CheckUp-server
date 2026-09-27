package com.checkup.checkup.global.exception;

import lombok.Getter;

/**
 * 서비스 규칙 위반을 {@link ErrorCode}로 알리는 예외. {@link GlobalExceptionHandler}가 오류 응답으로 변환한다.
 */
@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    /**
     * 오류 코드의 기본 메시지를 예외 메시지로 쓴다.
     *
     * @param errorCode 응답 상태·코드·메시지를 정하는 오류 코드
     */
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
