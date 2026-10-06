package com.checkup.checkup.domain.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00 KST에 보관 기간이 지난 웹훅 이벤트 ID 기록을 지운다(#149).
 *
 * 08:00 출석·알림 정리와 겹치지 않게 새벽에 돈다. 서버가 그 시각에 꺼져 있었을 수 있으므로 서버 시작 때도 한 번 지운다.
 * 로그에는 지운 개수만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventCleanupScheduler {

    private final WebhookService webhookService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteExpiredEvents() {
        int deleted = webhookService.deleteExpiredEvents();
        log.info("Deleted expired webhook events: count={}", deleted);
    }
}
