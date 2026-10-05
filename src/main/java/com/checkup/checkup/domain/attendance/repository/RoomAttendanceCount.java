package com.checkup.checkup.domain.attendance.repository;

/**
 * 호실 하나의 배정 인원과 출석 인원. 관리자 전개도의 호실 카드에 쓴다.
 */
public interface RoomAttendanceCount {

    /** 호실 번호. */
    Integer getDormitoryRoom();

    /** 그 호실에 배정된 학생 수. */
    long getAssigned();

    /** 배정된 학생 중 지금 출석 상태인 학생 수. */
    long getAttended();
}
