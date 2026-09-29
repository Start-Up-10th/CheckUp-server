package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DataGSM 웹훅 서명 규칙(raw body, UTF-8 secret, {@code sha256=} + 소문자 hex)대로
 * 서명을 검증하는지 확인한다.
 */
class WebhookSignatureVerifierTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte[] BODY =
            "{\"id\":\"evt_1\",\"event\":\"student.updated\"}".getBytes(StandardCharsets.UTF_8);

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET);

    @Test
    @DisplayName("같은 secret과 본문으로 만든 서명은 통과한다")
    void validSignatureIsAccepted() throws Exception {
        assertThat(verifier.verify(BODY, sign(SECRET, BODY))).isTrue();
    }

    @Test
    @DisplayName("다른 secret으로 만든 서명은 거부한다")
    void signatureWithOtherSecretIsRejected() throws Exception {
        assertThat(verifier.verify(BODY, sign("other-secret", BODY))).isFalse();
    }

    @Test
    @DisplayName("서명 헤더가 없으면 거부한다")
    void missingHeaderIsRejected() {
        assertThat(verifier.verify(BODY, null)).isFalse();
    }

    @Test
    @DisplayName("sha256= 접두어가 없으면 hex 값이 맞아도 거부한다")
    void headerWithoutPrefixIsRejected() throws Exception {
        String hexOnly = sign(SECRET, BODY).substring("sha256=".length());

        assertThat(verifier.verify(BODY, hexOnly)).isFalse();
    }

    @Test
    @DisplayName("서명 뒤에 본문이 바뀌면 거부한다")
    void tamperedBodyIsRejected() throws Exception {
        byte[] tampered = "{\"id\":\"evt_2\",\"event\":\"student.updated\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(tampered, sign(SECRET, BODY))).isFalse();
    }

    /** DataGSM과 같은 방식으로 서명 헤더 값을 만든다. */
    private static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
