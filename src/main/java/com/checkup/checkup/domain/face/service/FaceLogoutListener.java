package com.checkup.checkup.domain.face.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

/** Ends camera sessions operated by the logged-out administrator. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FaceLogoutListener {
    private final FaceRecognitionService faceRecognitionService;

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof Long memberId) {
            try {
                faceRecognitionService.closeAll(memberId);
            } catch (RuntimeException e) {
                log.warn("Face-session cleanup on logout failed: reason={}", e.getClass().getSimpleName());
            }
        }
    }
}
