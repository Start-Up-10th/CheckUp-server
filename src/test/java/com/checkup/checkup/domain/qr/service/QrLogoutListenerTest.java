package com.checkup.checkup.domain.qr.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;

/**
 * 로그아웃하면 그 관리자의 QR 세션만 모두 종료되는지 검증한다.
 */
class QrLogoutListenerTest {

    private final QrSessionService qrSessionService = mock(QrSessionService.class);
    private final QrLogoutListener listener = new QrLogoutListener(qrSessionService);

    @Test
    @DisplayName("로그아웃하면 그 회원의 QR 세션을 모두 종료한다")
    void logoutClosesAllSessionsOfMember() {
        listener.onLogout(new LogoutSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of())));

        verify(qrSessionService).closeAll(7L);
    }

    @Test
    @DisplayName("principal이 member id가 아니면 아무것도 하지 않는다")
    void nonMemberPrincipalDoesNothing() {
        listener.onLogout(new LogoutSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("anonymous", null, List.of())));

        verify(qrSessionService, never()).closeAll(any());
    }
}
