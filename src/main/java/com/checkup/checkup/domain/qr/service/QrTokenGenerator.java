package com.checkup.checkup.domain.qr.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 추측할 수 없는 QR 토큰을 만든다. 토큰에는 학생 정보를 넣지 않는다.
 */
@Component
public class QrTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = (TOKEN_BYTES * 4 + 2) / 3;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    private final SecureRandom random = new SecureRandom();

    /**
     * 이 생성기가 만드는 형식(base64url, 패딩 없음, 바이트 수에서 계산한 길이)인지 확인한다.
     * 스캔할 때 저장소를 조회하기 전에 형식이 다른 값을 걸러 내는 데 쓴다.
     */
    public static boolean isWellFormed(String token) {
        return token != null && TOKEN_FORMAT.matcher(token).matches();
    }

    /**
     * 32바이트 난수를 base64url(패딩 없음)로 인코딩한 43자 토큰을 만든다.
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
