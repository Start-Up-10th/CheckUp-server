package com.checkup.checkup.global.exception;

import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.exception.RateLimitException;
import team.themoment.datagsm.sdk.oauth.exception.ServerErrorException;

/**
 * DataGSM SDK 예외를 서버 오류 코드로 바꾼다. 전역 예외 처리와 로그인 콜백 리다이렉트가 같은 기준을 쓴다.
 *
 * DataGSM 401·403은 서버 설정 문제라 클라이언트 인증 실패로 돌려주지 않는다.
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
            default -> ErrorCode.DATAGSM_ERROR;
        };
    }
}
