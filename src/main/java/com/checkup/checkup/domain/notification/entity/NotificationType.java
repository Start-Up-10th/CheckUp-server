package com.checkup.checkup.domain.notification.entity;

/**
 * 웹 내부 알림 유형(REQ-COM-005). 이 세 가지 외의 알림은 만들지 않는다.
 *
 * - {@code ATTENDANCE}: 출석이 실제로 성공 처리됐을 때
 * - {@code VOLUNTEER}: 봉사 횟수가 +1 적립됐을 때
 * - {@code NOTICE}: 새 공지가 등록됐을 때(공지 알림 수신 동의 학생만)
 */
public enum NotificationType {
    ATTENDANCE,
    VOLUNTEER,
    NOTICE
}
