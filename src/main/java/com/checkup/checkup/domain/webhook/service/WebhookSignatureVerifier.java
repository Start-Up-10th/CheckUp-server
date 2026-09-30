package com.checkup.checkup.domain.webhook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * DataGSM 웹훅 요청의 {@code X-DataGSM-Signature}를 검증한다.
 *
 * <p>서명은 {@code sha256=<HMAC-SHA256(secret, 요청 본문 원문 바이트)의 소문자 hex>} 형식이다.
 * secret은 hex 디코딩하지 않고 UTF-8 문자열 그대로 키로 쓴다.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    /**
     * @param secret DataGSM 이벤트 등록 때 발급된 웹훅 secret
     */
    public WebhookSignatureVerifier(
            @Value("${datagsm.webhook-secret}") String secret
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 요청 본문과 서명 헤더가 일치하는지 확인한다. 비교는 타이밍 공격을 피하도록 상수 시간으로 한다.
     *
     * @param body            JSON 파싱 전의 요청 본문 원문
     * @param signatureHeader {@code X-DataGSM-Signature} 헤더 값. 없으면 {@code null}
     * @return 서명이 일치하면 {@code true}, 헤더가 없거나 형식이 다르거나 일치하지 않으면 {@code false}
     */
    public boolean verify(byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String received = signatureHeader.substring(PREFIX.length());

        String expected = hmacHex(body);

        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 본문의 HMAC-SHA256 값을 소문자 hex 문자열로 계산한다.
     */
    private String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] raw = mac.doFinal(body);
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없었습니다.", e);
        }
    }
}
