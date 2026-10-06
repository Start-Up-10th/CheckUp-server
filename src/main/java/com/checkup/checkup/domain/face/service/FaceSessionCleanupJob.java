package com.checkup.checkup.domain.face.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 연결이 끊기거나 요청 시간이 초과돼 버려진 카메라 세션의 AI 쪽 삭제를 다시 시도한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceSessionCleanupJob {
    private final FaceRecognitionService faceRecognitionService;

    @Scheduled(fixedDelayString = "${checkup.face.cleanup-interval-ms:60000}")
    public void cleanupIdleSessions() {
        try {
            faceRecognitionService.cleanupIdleSessions();
        } catch (RuntimeException e) {
            log.warn("Idle face-session cleanup pass failed: reason={}", e.getClass().getSimpleName());
        }
    }
}
