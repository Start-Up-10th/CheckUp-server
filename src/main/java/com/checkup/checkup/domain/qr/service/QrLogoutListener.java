package com.checkup.checkup.domain.qr.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 로그아웃하면 그 회원이 운영하던 QR 세션을 모두 종료한다.
 */
@Component
@RequiredArgsConstructor
public class QrLogoutListener {

    private final QrSessionService qrSessionService;

    /**
     * 세션 principal(member id)로 QR 세션을 정리한다. principal이 member id가 아니면 무시한다.
     */
    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof Long memberId) {
            qrSessionService.closeAll(memberId);
        }
    }
}
