package com.checkup.checkup.domain.attendance.entity;

/**
 * 출석 용도. 용도가 다르면 같은 학생·운영일이라도 출석을 따로 기록한다(REQ-ATT-001).
 */
public enum AttendancePurpose {
    DORMITORY,
    STUDY_ROOM
}
