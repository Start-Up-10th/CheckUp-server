package com.checkup.checkup.domain.face.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries remote deletion for abandoned camera sessions after disconnects and request timeouts. */
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
