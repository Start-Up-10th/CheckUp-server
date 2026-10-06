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
    INVALID_ORIGIN(HttpStatus.FORBIDDEN, "허용되지 않은 출처의 요청입니다."),
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
    INVALID_WEBHOOK_PAYLOAD(HttpStatus.BAD_REQUEST, "웹훅 본문 형식이 올바르지 않습니다."),

    // 학생
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생을 찾을 수 없습니다."),
    STUDENT_NOT_IN_ROOM(HttpStatus.BAD_REQUEST, "해당 호실의 학생이 아닙니다."),

    // 봉사
    VOLUNTEER_COUNT_ZERO(HttpStatus.CONFLICT, "봉사 횟수가 0이라 차감할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "다른 요청에 이미 쓴 재시도 키입니다."),
    NO_VOLUNTEER_LEFT(HttpStatus.CONFLICT, "봉사가 없습니다."),
    ALREADY_ON_DUTY(HttpStatus.CONFLICT, "이미 오늘 봉사자로 지정된 학생입니다."),
    NOT_ON_DUTY(HttpStatus.NOT_FOUND, "오늘 봉사자로 지정되지 않은 학생입니다."),
    DUTY_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 봉사를 완료했습니다."),

    // QR
    QR_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 세션이 없거나 종료되었습니다."),

    // 얼굴 인식
    FACE_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "얼굴 정보 처리 동의가 필요합니다."),
    FACE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "얼굴 정보가 이미 등록되어 있습니다."),
    FACE_ENROLLMENT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "현재 얼굴 등록 대상이 아닙니다."),
    FACE_ENROLLMENT_MULTIPLE_IDENTITIES(HttpStatus.UNPROCESSABLE_ENTITY, "영상에 얼굴이 여러 명 포함되어 있습니다. 본인만 촬영해 주세요."),
    FACE_ENROLLMENT_LOW_LIGHT(HttpStatus.UNPROCESSABLE_ENTITY, "영상이 어둡습니다. 밝은 곳에서 다시 촬영해 주세요."),
    FACE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "얼굴 인식 세션이 없거나 종료되었습니다."),
    FACE_NO_ENROLLED_STUDENTS(HttpStatus.CONFLICT, "현재 인식할 수 있는 등록 학생이 없습니다."),
    FACE_TOO_MANY_CANDIDATES(HttpStatus.UNPROCESSABLE_ENTITY, "인식 대상 학생이 허용 인원을 초과했습니다."),
    FACE_ENROLLMENT_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "얼굴 등록 영상의 품질이 기준에 맞지 않습니다."),
    FACE_INVALID_MEDIA(HttpStatus.BAD_REQUEST, "지원하지 않거나 읽을 수 없는 영상·이미지입니다."),
    FACE_UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 크기 제한을 초과했습니다."),
    FACE_INVALID_FRAME(HttpStatus.UNPROCESSABLE_ENTITY, "얼굴 인식 프레임을 처리할 수 없습니다."),
    FACE_FRAME_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "프레임 요청이 너무 빠릅니다."),
    FACE_SERVICE_BUSY(HttpStatus.TOO_MANY_REQUESTS, "얼굴 인식 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
    FACE_AI_BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "얼굴 인식 서버 응답을 처리하지 못했습니다."),
    FACE_AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "얼굴 인식 서버를 사용할 수 없습니다."),
    FACE_AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "얼굴 인식 서버 응답 시간이 초과되었습니다."),
    FACE_FRAME_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "얼굴 인식 프레임을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    public String getCode() {
        return name();
    }
}
