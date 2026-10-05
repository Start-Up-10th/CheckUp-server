package com.checkup.checkup.domain.attendance.dto.response;

import com.checkup.checkup.domain.attendance.entity.Attendance;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 학생 본인의 오늘 운영일 출석 상태. 용도마다 따로 준다(REQ-ATT-001).
 *
 * @param operatingDay 오늘 운영일(08:00 KST에 바뀐다)
 * @param dormitory    기숙사 입소 출석 상태
 * @param studyRoom    자습실 출석 상태
 */
public record MyAttendanceResponse(
        LocalDate operatingDay,
        Status dormitory,
        Status studyRoom
) {

    /**
     * 용도 하나의 출석 상태.
     *
     * @param attended        지금 출석 상태인지. 기록이 없거나 관리자가 미출석으로 바꿨으면 false
     * @param firstVerifiedAt 최초 유효 인증 시각. 출석 상태가 아니거나 인증 없이 수동으로만 출석했으면 null
     */
    public record Status(boolean attended, Instant firstVerifiedAt) {

        private static final Status ABSENT = new Status(false, null);

        private static Status from(Attendance attendance) {
            return attendance.isAttended() ? new Status(true, attendance.getFirstVerifiedAt()) : ABSENT;
        }
    }

    public static MyAttendanceResponse of(LocalDate operatingDay, List<Attendance> attendances) {
        return new MyAttendanceResponse(
                operatingDay,
                status(attendances, AttendancePurpose.DORMITORY),
                status(attendances, AttendancePurpose.STUDY_ROOM));
    }

    private static Status status(List<Attendance> attendances, AttendancePurpose purpose) {
        return attendances.stream()
                .filter(attendance -> attendance.getPurpose() == purpose)
                .findFirst()
                .map(Status::from)
                .orElse(Status.ABSENT);
    }
}
