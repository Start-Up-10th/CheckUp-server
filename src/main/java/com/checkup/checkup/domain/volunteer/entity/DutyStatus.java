package com.checkup.checkup.domain.volunteer.entity;

/**
 * 당일 봉사자 지정 상태(REQ-COM-006).
 */
public enum DutyStatus {
    /** 사감이 오늘 봉사자로 지정했다. 아직 봉사 완료를 확인하지 않았다. */
    ASSIGNED,
    /** 자치위원이 봉사 완료를 확인했다. 이때 봉사 횟수를 1 줄였다. */
    COMPLETED,
    /**
     * 봉사를 하지 않았다(#250). 관리자가 미이행으로 처리했거나, 운영일이 지나도록 완료·미이행 처리가 없던 지정이다.
     * 뒤의 경우는 저장값이 {@code ASSIGNED}이고 조회할 때만 미이행으로 본다.
     */
    MISSED
}
