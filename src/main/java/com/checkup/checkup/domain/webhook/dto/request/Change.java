package com.checkup.checkup.domain.webhook.dto.request;

/**
 * 웹훅 이벤트의 변경 한 건.
 *
 * @param index  {@code old}·{@code new} 항목을 짝짓는 번호
 * @param object 변경 시점의 학생 전체 정보. 생성·삭제 쪽은 빈 객체라 필드가 모두 {@code null}이다.
 */
public record Change(
        int index,
        WebhookStudent object
) {
}
