package com.checkup.checkup.domain.face.ai;

import com.checkup.checkup.domain.face.config.FaceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.function.Supplier;

/** The only Spring component that communicates with the private FastAPI service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFaceClient {
    private static final String ENROLLMENT_PATH = "/internal/v1/face/enrollments/extract";
    private static final String SESSION_PATH = "/internal/v1/face/sessions/{sessionId}";
    private static final String FRAME_PATH = "/internal/v1/face/sessions/{sessionId}/frames";

    private final RestClient faceAiRestClient;
    private final FaceProperties properties;
    private final ObjectMapper objectMapper;

    public AiFaceEnrollmentResponse extract(byte[] video, MediaType contentType) {
        return invoke("enrollment", () -> faceAiRestClient.post()
                .uri(ENROLLMENT_PATH)
                .headers(this::addServiceHeaders)
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON)
                .body(video)
                .retrieve()
                .body(AiFaceEnrollmentResponse.class));
    }

    public void createSession(UUID sessionId, AiFaceSessionRequest request) {
        AiFaceStatusResponse response = invoke("session_create", () -> faceAiRestClient.put()
                .uri(SESSION_PATH, sessionId)
                .headers(this::addServiceHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiFaceStatusResponse.class));
        if (response == null || !"ready".equals(response.status())) {
            throw new AiFaceException(502, "session_create", "invalid_response");
        }
    }

    public AiFaceFrameResponse recognize(
            UUID sessionId,
            String frameId,
            byte[] image,
            MediaType contentType
    ) {
        AiFaceFrameResponse response = invoke("frame", () -> faceAiRestClient.post()
                .uri(FRAME_PATH, sessionId)
                .headers(headers -> {
                    addServiceHeaders(headers);
                    headers.set("X-Frame-Id", frameId);
                })
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON)
                .body(image)
                .retrieve()
                .body(AiFaceFrameResponse.class));
        if (response == null || response.faces() == null || response.frameId() == null) {
            throw new AiFaceException(502, "frame", "invalid_response");
        }
        return response;
    }

    public void deleteSession(UUID sessionId) {
        AiFaceStatusResponse response = invoke("session_delete", () -> faceAiRestClient.delete()
                .uri(SESSION_PATH, sessionId)
                .headers(this::addServiceHeaders)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AiFaceStatusResponse.class));
        if (response == null || !"deleted".equals(response.status())) {
            throw new AiFaceException(502, "session_delete", "invalid_response");
        }
    }

    private void addServiceHeaders(HttpHeaders headers) {
        headers.setBearerAuth(properties.serviceToken());
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    private <T> T invoke(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String errorCode = parseErrorCode(e.getResponseBodyAsByteArray());
            log.warn("Face AI request failed: operation={}, status={}, errorCode={}",
                    operation, e.getStatusCode().value(), errorCode);
            throw new AiFaceException(e.getStatusCode().value(), operation, errorCode);
        } catch (ResourceAccessException e) {
            int status = isTimeout(e) ? 504 : 503;
            log.warn("Face AI request unavailable: operation={}, timeout={}", operation, status == 504);
            throw new AiFaceException(status, operation, status == 504 ? "timeout" : "unavailable");
        }
    }

    private String parseErrorCode(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detail = root == null ? null : root.get("detail");
            if (detail != null && detail.isObject() && detail.get("code") != null) {
                return safeCode(detail.get("code").asText());
            }
            if (detail != null && detail.isArray()) {
                return "validation_error";
            }
        } catch (tools.jackson.core.JacksonException ignored) {
            // Do not log or return an unparseable upstream body.
        }
        return "unspecified";
    }

    private static String safeCode(String value) {
        if (value != null && value.matches("[A-Za-z0-9_.-]{1,80}")) {
            return value;
        }
        return "unspecified";
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
