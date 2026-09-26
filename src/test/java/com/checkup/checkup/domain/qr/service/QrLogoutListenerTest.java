package com.checkup.checkup.domain.qr.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;

class QrLogoutListenerTest {

    private final QrSessionService qrSessionService = mock(QrSessionService.class);
    private final QrLogoutListener listener = new QrLogoutListener(qrSessionService);

    @Test
    void 로그아웃하면_그_회원의_QR_세션을_모두_종료한다() {
        listener.onLogout(new LogoutSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of())));

        verify(qrSessionService).closeAll(7L);
    }

    @Test
    void principal이_member_id가_아니면_아무것도_하지_않는다() {
        listener.onLogout(new LogoutSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("anonymous", null, List.of())));

        verify(qrSessionService, never()).closeAll(any());
    }
}
