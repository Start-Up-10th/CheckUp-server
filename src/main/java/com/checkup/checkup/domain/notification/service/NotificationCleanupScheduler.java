package com.checkup.checkup.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 08:00 KST 운영일 경계에 지난 운영일 출석 알림을 지운다(DEC-009).
 *
 * 서버가 그 시각에 꺼져 있었으면 다음 실행 때 함께 지운다. 로그에는 지운 개수만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void deleteExpiredAttendance() {
        int deleted = notificationService.deleteExpiredAttendance();
        log.info("Deleted expired attendance notifications: count={}", deleted);
    }
}
