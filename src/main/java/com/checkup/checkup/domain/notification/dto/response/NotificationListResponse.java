package com.checkup.checkup.domain.notification.dto.response;

import java.util.List;

/**
 * 학생 본인의 알림 목록. 최근 50개를 최신순으로 담는다.
 *
 * @param hasUnread     읽지 않은 알림이 하나라도 있으면 {@code true}
 * @param notifications 최신순 알림 목록
 */
public record NotificationListResponse(
        boolean hasUnread,
        List<NotificationResponse> notifications
) {}
