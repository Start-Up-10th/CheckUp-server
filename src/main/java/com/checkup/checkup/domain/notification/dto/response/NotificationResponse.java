package com.checkup.checkup.domain.notification.dto.response;

import com.checkup.checkup.domain.notification.entity.Notification;
import com.checkup.checkup.domain.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "알림 id", example = "1") Long id,
        @Schema(description = "알림 유형. ATTENDANCE(출석 완료), VOLUNTEER(당일 봉사자 지정), NOTICE(공지)", example = "ATTENDANCE") NotificationType type,
        @Schema(description = "화면에 그대로 표시할 문구", example = "기숙사 출석이 완료됐어요") String message,
        @Schema(description = "만든 시각(ISO-8601 UTC). 웹은 이 값으로 상대 시각을 계산한다", example = "2026-10-06T12:00:00Z") Instant createdAt,
        @Schema(description = "읽었으면 true") boolean read
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
