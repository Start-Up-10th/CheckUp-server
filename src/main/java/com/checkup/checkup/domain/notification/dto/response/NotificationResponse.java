package com.checkup.checkup.domain.notification.dto.response;

import com.checkup.checkup.domain.notification.entity.Notification;
import com.checkup.checkup.domain.notification.entity.NotificationType;

import java.time.Instant;

/**
 * 알림 목록의 알림 한 건.
 *
 * @param id        알림 id
 * @param type      알림 유형
 * @param message   화면에 그대로 표시할 문구
 * @param createdAt 만든 시각(ISO-8601 UTC). 웹은 이 값으로 상대 시각을 계산한다.
 * @param read      읽었으면 {@code true}
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        Instant createdAt,
        boolean read
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getCreatedAt(),
                n.isRead());
    }
}
