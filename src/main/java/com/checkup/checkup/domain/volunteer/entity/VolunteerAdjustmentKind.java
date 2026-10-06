package com.checkup.checkup.domain.volunteer.entity;

/**
 * 봉사 횟수 조정의 종류(#176). 관리자 화면은 종류별 기본 문구를 활동명으로 쓴다.
 *
 * - {@code ADMIN}: 관리자의 {@code +}/{@code −}·여러 회 조정.
 * - {@code DUTY_COMPLETION}: 자치위원이 당일 봉사 완료를 확인해 생긴 차감(-1).
 */
public enum VolunteerAdjustmentKind {
    ADMIN,
    DUTY_COMPLETION
}
