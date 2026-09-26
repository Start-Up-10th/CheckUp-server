package com.checkup.checkup.domain.qr.entity;

/**
 * QR 출석 용도. 용도가 다르면 같은 학생이라도 출석을 따로 처리한다.
 */
public enum QrPurpose {
    DORMITORY,
    STUDY_ROOM
}
