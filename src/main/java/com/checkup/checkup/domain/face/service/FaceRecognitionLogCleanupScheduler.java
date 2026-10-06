package com.checkup.checkup.domain.face.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 08:00 KST 운영일 경계에 지난 운영일의 최근 인식 기록을 지운다(#143, REQ-ATT-007).
 *
 * 서버가 그 시각에 꺼져 있었을 수 있으므로 서버 시작 때도 한 번 지운다. 로그에는 지운 개수만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceRecognitionLogCleanupScheduler {

    private final FaceRecognitionLogService faceRecognitionLogService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = faceRecognitionLogService.deleteExpired();
        log.info("Deleted expired face recognition logs: count={}", deleted);
    }
}
