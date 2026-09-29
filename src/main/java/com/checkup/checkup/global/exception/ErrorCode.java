package com.checkup.checkup.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서버 오류 코드. 응답의 {@code code}는 enum 이름, {@code message}는 기본 메시지다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "state가 유효하지 않거나 만료되었습니다."),
    INACTIVE_ACCOUNT(HttpStatus.FORBIDDEN, "올바르지 않은 계정 상태입니다."),
    MISSING_STUDENT_INFO(HttpStatus.FORBIDDEN, "학생 정보가 없습니다."),
    UNSUPPORTED_ACCOUNT(HttpStatus.FORBIDDEN, "이용 권한이 없는 계정입니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다."),

    // DataGSM
    DATAGSM_INVALID_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 유효하지 않거나 만료되었습니다."),
    DATAGSM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DataGSM에 일시적으로 연결할 수 없습니다."),
    DATAGSM_ERROR(HttpStatus.BAD_GATEWAY, "DataGSM 요청을 처리하지 못했습니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다."),

    // QR
    QR_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 세션이 없거나 종료되었습니다.");

    private final HttpStatus status;
    private final String message;

    public String getCode() {
        return name();
    }
}
