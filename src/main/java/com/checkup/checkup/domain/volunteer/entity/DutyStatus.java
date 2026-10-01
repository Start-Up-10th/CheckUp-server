package com.checkup.checkup.domain.volunteer.entity;

/**
 * 당일 봉사자 지정 상태(REQ-COM-006).
 */
public enum DutyStatus {
    /** 사감이 오늘 봉사자로 지정했다. 아직 봉사 완료를 확인하지 않았다. */
    ASSIGNED,
    /** 자치위원이 봉사 완료를 확인했다. 이때 봉사 횟수를 1 줄였다. */
    COMPLETED
}
