package com.checkup.checkup.domain.attendance.entity;

/**
 * 자동 인증(QR·얼굴)을 출석으로 기록한 결과.
 */
public enum AttendanceRecordResult {

    /** 새로 출석 처리했다. */
    RECORDED,

    /** 이미 출석 상태라 바꾸지 않았다. */
    ALREADY_ATTENDED,

    /** 관리자가 미출석으로 바꾸기 전에 발생한 인증이라 무시했다(DEC-008). */
    SUPERSEDED_BY_MANUAL,

    /** 오늘이 아닌 운영일에 발생한 인증이라 기록하지 않았다(REQ-ATT-007). */
    STALE,

    /** 인증 시각이 서버 현재 시각보다 허용 오차 이상 늦어 기록하지 않았다(기기 시계 오차). */
    FUTURE
}
