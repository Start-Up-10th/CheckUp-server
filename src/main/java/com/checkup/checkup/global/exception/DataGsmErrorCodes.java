package com.checkup.checkup.global.exception;

import java.io.IOException;
import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.exception.RateLimitException;
import team.themoment.datagsm.sdk.oauth.exception.ServerErrorException;

/**
 * DataGSM SDK 예외를 서버 오류 코드로 바꾼다. 전역 예외 처리, 로그인 콜백 리다이렉트, 학생 조회가 같은 기준을 쓴다.
 *
 * DataGSM 401·403은 서버 설정 문제라 클라이언트 인증 실패로 돌려주지 않는다.
 * 연결 실패·시간 초과는 DataGSM 서버 오류와 같이 일시적으로 연결할 수 없는 상태로 본다.
 */
public final class DataGsmErrorCodes {

    private DataGsmErrorCodes() {
    }

    /**
     * DataGSM 예외에 맞는 오류 코드를 반환한다.
     */
    public static ErrorCode of(DataGsmException e) {
        return switch (e) {
            case BadRequestException ignored -> ErrorCode.DATAGSM_INVALID_CODE;
            case ServerErrorException ignored -> ErrorCode.DATAGSM_UNAVAILABLE;
            case RateLimitException ignored -> ErrorCode.DATAGSM_UNAVAILABLE;
            default -> isConnectionFailure(e) ? ErrorCode.DATAGSM_UNAVAILABLE : ErrorCode.DATAGSM_ERROR;
        };
    }

    /**
     * DataGSM OpenAPI(학생 조회) 예외에 맞는 오류 코드를 반환한다. 로그인(OAuth)과 같은 기준이다.
     * OpenAPI에는 인가 코드가 없어 잘못된 요청(400)도 {@code DATAGSM_ERROR}다.
     */
    public static ErrorCode of(team.themoment.datagsm.sdk.openapi.exception.DataGsmException e) {
        return switch (e) {
            case team.themoment.datagsm.sdk.openapi.exception.ServerErrorException ignored ->
                    ErrorCode.DATAGSM_UNAVAILABLE;
            case team.themoment.datagsm.sdk.openapi.exception.RateLimitException ignored ->
                    ErrorCode.DATAGSM_UNAVAILABLE;
            default -> e.getCause() instanceof IOException ? ErrorCode.DATAGSM_UNAVAILABLE : ErrorCode.DATAGSM_ERROR;
        };
    }

    /**
     * DataGSM OpenAPI 예외를 로그에 남길 형태로 줄인다. 예외 종류, HTTP 상태(SDK가 알려 주지 않으면 0), 원인 예외 종류만 담는다.
     * SDK 메시지에는 DataGSM 응답 본문이 섞일 수 있어 넣지 않는다.
     */
    public static String describe(team.themoment.datagsm.sdk.openapi.exception.DataGsmException e) {
        String cause = e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName();
        return "type=" + e.getClass().getSimpleName() + ", status=" + e.getStatusCode() + ", cause=" + cause;
    }

    /**
     * DataGSM에 닿지 못했거나 시간 안에 응답을 받지 못한 경우다. SDK는 이때 HTTP 상태 없이 IOException을 감싸서 던진다.
     */
    private static boolean isConnectionFailure(DataGsmException e) {
        return !e.hasStatusCode() && e.getCause() instanceof IOException;
    }
}
