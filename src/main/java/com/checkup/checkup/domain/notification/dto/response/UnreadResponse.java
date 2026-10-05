package com.checkup.checkup.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 홈 헤더 벨 빨간 점·사이드바 강조에 쓰는 미확인 여부.
 *
 * @param hasUnread 읽지 않은 알림이 하나라도 있으면 {@code true}
 */
public record UnreadResponse(
        @Schema(description = "읽지 않은 알림이 하나라도 있으면 true") boolean hasUnread
) {
}
