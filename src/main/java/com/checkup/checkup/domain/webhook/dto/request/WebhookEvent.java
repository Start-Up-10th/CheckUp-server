package com.checkup.checkup.domain.webhook.dto.request;

import java.time.Instant;

/**
 * DataGSM 웹훅 이벤트 본문.
 *
 * @param id        이벤트 고유 ID({@code evt_...}). 중복 수신을 거르는 키로 쓴다.
 * @param event     이벤트 종류(예: {@code student.updated})
 * @param timestamp 이벤트 발생 시각(UTC)
 * @param data      변경 목록
 */
public record WebhookEvent(
        String id,
        String event,
        Instant timestamp,
        Data data
) {
}
