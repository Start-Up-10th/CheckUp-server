package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkup.checkup.domain.webhook.dto.request.WebhookEvent;
import com.checkup.checkup.domain.webhook.dto.request.WebhookStudent;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DataGSM 문서의 {@code student.updated} 예시가 DTO로 읽히고,
 * 처리하지 않는 이벤트는 무시하며, 읽을 수 없거나 필수값이 없는 본문은 400으로 거부하는지 검증한다.
 */
class WebhookServiceTest {

    /** DataGSM 문서 예시 A(호실 변경). 이름·이메일은 문서의 가짜 값이다. */
    private static final String STUDENT_UPDATED = """
            {
              "id": "evt_3f9a2c1b8e0d4a7f9c2e1b6d5a4f3c2e",
              "event": "student.updated",
              "timestamp": "2026-06-23T05:21:48.123Z",
              "data": {
                "old": [
                  { "index": 0, "object": {
                    "student_id": 42, "name": "홍길동", "email": "s24080@gsm.hs.kr", "sex": "MAN",
                    "grade": 2, "class_num": 1, "number": 5, "student_number": 2105,
                    "role": "GENERAL_STUDENT", "dormitory_floor": 2, "dormitory_room": 201 } }
                ],
                "new": [
                  { "index": 0, "object": {
                    "student_id": 42, "name": "홍길동", "email": "s25012@gsm.hs.kr", "sex": "MAN",
                    "grade": 2, "class_num": 1, "number": 5, "student_number": 2105,
                    "role": "GENERAL_STUDENT", "dormitory_floor": 3, "dormitory_room": 301 } }
                ]
              }
            }
            """;

    /** DataGSM 문서 예시 B(졸업). 학년·반·번호·호실이 null이 된다. */
    private static final String GRADUATED = """
            {
              "id": "evt_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
              "event": "student.updated",
              "timestamp": "2026-06-23T05:30:00.000Z",
              "data": {
                "new": [
                  { "index": 0, "object": {
                    "student_id": 42, "name": "홍길동", "email": "s24080@gsm.hs.kr", "sex": "MAN",
                    "grade": null, "class_num": null, "number": null, "student_number": null,
                    "role": "GRADUATE", "dormitory_floor": null, "dormitory_room": null } }
                ]
              }
            }
            """;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final WebhookService webhookService = new WebhookService(objectMapper);

    @Test
    @DisplayName("문서 예시의 student.updated 본문을 DTO로 읽는다")
    void studentUpdatedPayloadIsReadIntoDto() {
        WebhookEvent event = objectMapper.readValue(STUDENT_UPDATED, WebhookEvent.class);

        assertThat(event.id()).isEqualTo("evt_3f9a2c1b8e0d4a7f9c2e1b6d5a4f3c2e");
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-06-23T05:21:48.123Z"));
        assertThat(event.data().after()).hasSize(1);

        WebhookStudent student = event.data().after().getFirst().object();
        assertThat(student.studentId()).isEqualTo(42L);
        assertThat(student.classNum()).isEqualTo(1);
        assertThat(student.studentNumber()).isEqualTo(2105);
        assertThat(student.dormitoryRoom()).isEqualTo(301);
        assertThat(student.role()).isEqualTo("GENERAL_STUDENT");
    }

    @Test
    @DisplayName("졸업으로 학년·호실이 null이어도 읽을 수 있다")
    void graduationWithNullFieldsIsRead() {
        WebhookStudent student = objectMapper.readValue(GRADUATED, WebhookEvent.class)
                .data().after().getFirst().object();

        assertThat(student.role()).isEqualTo("GRADUATE");
        assertThat(student.grade()).isNull();
        assertThat(student.dormitoryRoom()).isNull();
    }

    @Test
    @DisplayName("student.updated 본문은 예외 없이 처리한다")
    void studentUpdatedIsHandled() {
        assertThatCode(() -> webhookService.handle(bytes(STUDENT_UPDATED))).doesNotThrowAnyException();
        assertThatCode(() -> webhookService.handle(bytes(GRADUATED))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("처리하지 않는 이벤트는 오류 없이 무시한다")
    void otherEventIsIgnored() {
        String clubUpdated = """
                {"id":"evt_1","event":"club.updated","timestamp":"2026-06-23T05:21:48Z","data":{"new":[]}}
                """;

        assertThatCode(() -> webhookService.handle(bytes(clubUpdated))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 객체({}) 항목이 섞여 있어도 오류 없이 처리한다")
    void emptyObjectIsSkipped() {
        String withEmpty = """
                {"id":"evt_1","event":"student.updated","timestamp":"2026-06-23T05:21:48Z",
                 "data":{"new":[{"index":0,"object":{}},{"index":1,"object":{"student_id":42,"name":"홍길동"}}]}}
                """;

        assertThatCode(() -> webhookService.handle(bytes(withEmpty))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JSON으로 읽을 수 없는 본문은 400 INVALID_WEBHOOK_PAYLOAD로 거부한다")
    void malformedJsonIsRejected() {
        assertRejected("{not json");
    }

    @Test
    @DisplayName("id가 없으면 400 INVALID_WEBHOOK_PAYLOAD로 거부한다")
    void missingIdIsRejected() {
        assertRejected("""
                {"event":"student.updated","timestamp":"2026-06-23T05:21:48Z","data":{"new":[]}}
                """);
    }

    @Test
    @DisplayName("timestamp가 없으면 400 INVALID_WEBHOOK_PAYLOAD로 거부한다")
    void missingTimestampIsRejected() {
        assertRejected("""
                {"id":"evt_1","event":"student.updated","data":{"new":[]}}
                """);
    }

    @Test
    @DisplayName("data가 없으면 400 INVALID_WEBHOOK_PAYLOAD로 거부한다")
    void missingDataIsRejected() {
        assertRejected("""
                {"id":"evt_1","event":"student.updated","timestamp":"2026-06-23T05:21:48Z"}
                """);
    }

    private void assertRejected(String body) {
        assertThatThrownBy(() -> webhookService.handle(bytes(body)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_WEBHOOK_PAYLOAD));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
