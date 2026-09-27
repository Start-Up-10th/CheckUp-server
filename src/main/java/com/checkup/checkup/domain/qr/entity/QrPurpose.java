package com.checkup.checkup.domain.qr.entity;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

/**
 * QR 출석 용도. 용도가 다르면 같은 학생이라도 출석을 따로 처리한다.
 */
public enum QrPurpose {
    DORMITORY(AttendancePurpose.DORMITORY),
    STUDY_ROOM(AttendancePurpose.STUDY_ROOM);

    private final AttendancePurpose attendancePurpose;

    QrPurpose(AttendancePurpose attendancePurpose) {
        this.attendancePurpose = attendancePurpose;
    }

    /**
     * 이 QR로 기록할 출석 용도.
     */
    public AttendancePurpose toAttendancePurpose() {
        return attendancePurpose;
    }
}
