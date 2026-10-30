package com.checkup.checkup.domain.attendance.repository;

/** 층 전체 명단을 추가 엔티티 조회 없이 읽는 학생별 출석 결과. studentId는 정렬에 쓰는 내부 DB id다. */
public record FloorStudentAttendance(
        Long studentId,
        int dormitoryRoom,
        Long datagsmStudentId,
        String studentName,
        int studentClass,
        int studentNumber,
        boolean attended
) {
}
