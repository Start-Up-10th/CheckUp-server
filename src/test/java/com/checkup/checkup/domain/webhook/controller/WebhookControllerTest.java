package com.checkup.checkup.domain.webhook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.webhook.service.StudentManualSyncService;
import com.checkup.checkup.domain.webhook.service.WebhookService;
import com.checkup.checkup.domain.webhook.service.WebhookSignatureVerifier;
import com.checkup.checkup.global.security.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹훅 API가 로그인 없이 호출되고, 서명이 맞으면 본문을 서비스로 넘겨 204,
 * 틀리거나 없으면 서비스를 호출하지 않고 401로 응답하는지 검증한다.
 */
@WebMvcTest(controllers = WebhookController.class, properties = "datagsm.webhook-secret=" + WebhookControllerTest.SECRET)
@Import({SecurityConfig.class, WebhookSignatureVerifier.class})
class WebhookControllerTest {

    static final String SECRET = "test-webhook-secret";
    private static final String WEBHOOK = "/api/v1/webhook";
    private static final String SIGNATURE_HEADER = "X-DataGSM-Signature";
    private static final String BODY = "{\"id\":\"evt_1\",\"event\":\"student.updated\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookService webhookService;

    @MockitoBean
    private StudentManualSyncService studentManualSyncService;

    @Test
    @DisplayName("로그인하지 않아도 서명이 맞으면 204로 응답한다")
    void validSignatureWithoutLoginReturnsNoContent() throws Exception {
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(BODY))
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(webhookService).handle(BODY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("서명이 틀리면 401 INVALID_WEBHOOK_SIGNATURE로 응답한다")
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "sha256=" + "0".repeat(64))
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        verify(webhookService, never()).handle(any());
    }

    @Test
    @DisplayName("서명 헤더가 없으면 400이 아니라 401 INVALID_WEBHOOK_SIGNATURE로 응답한다")
    void missingSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        verify(webhookService, never()).handle(any());
    }

    @Test
    @DisplayName("서명한 본문과 다른 본문을 보내면 401로 응답한다")
    void tamperedBodyReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(BODY))
                        .content(BODY.replace("evt_1", "evt_2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        verify(webhookService, never()).handle(any());
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
