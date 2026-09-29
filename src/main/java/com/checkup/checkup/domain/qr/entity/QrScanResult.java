package com.checkup.checkup.domain.qr.entity;

/**
 * QR 스캔 판정 결과(하네스 DEC-018). 모두 200으로 응답하고 웹이 결과별 문구를 표시한다.
 */
public enum QrScanResult {

    /** 새로 출석 처리됐다. */
    APPROVED,

    /** 같은 학생·용도·운영일에 이미 출석했다. */
    DUPLICATE,

    /** 토큰의 발급 때 정한 만료 시각이 지났다. */
    EXPIRED,

    /** 토큰은 유효하지만 발급한 QR 세션이 종료됐거나 lease가 끝났다. */
    CLOSED,

    /** 발급하지 않았거나 기록이 남아 있지 않은 토큰이다. */
    INVALID
}
