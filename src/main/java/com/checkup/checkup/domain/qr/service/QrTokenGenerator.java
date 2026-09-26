package com.checkup.checkup.domain.qr.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 추측할 수 없는 QR 토큰을 만든다. 토큰에는 학생 정보를 넣지 않는다.
 */
@Component
public class QrTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /**
     * 32바이트 난수를 base64url(패딩 없음)로 인코딩한 43자 토큰을 만든다.
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
