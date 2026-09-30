package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.StudentSyncService;
import com.checkup.checkup.domain.webhook.dto.request.WebhookEvent;
import com.checkup.checkup.domain.webhook.dto.request.WebhookStudent;
import com.checkup.checkup.domain.webhook.repository.WebhookEventLogRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DataGSM 문서의 {@code student.updated} 예시가 DTO로 읽히고,
 * 처리하지 않는 이벤트와 이미 처리한 이벤트 ID는 무시하며, 읽을 수 없거나 필수값이 없는 본문은 400으로 거부하는지 검증한다.
 * 저장된 학생의 정보·권한 동기화, 졸업·자퇴 처리와 이벤트 발행, 오래된 이벤트와 부분 데이터 건너뛰기도 검증한다.
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

    private static final Instant NOW = Instant.parse("2026-06-23T05:22:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final WebhookEventLogRepository webhookEventLogRepository = mock(WebhookEventLogRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final WebhookService webhookService = new WebhookService(
            objectMapper, webhookEventLogRepository, Clock.fixed(NOW, ZoneOffset.UTC),
            new StudentSyncService(studentRepository, eventPublisher));

    @BeforeEach
    void setUp() {
        given(webhookEventLogRepository.record(anyString(), any())).willReturn(1);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of());
    }

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
    @DisplayName("student.updated 본문은 이벤트 ID를 현재 시각으로 기록하고 처리한다")
    void studentUpdatedIsRecordedAndHandled() {
        assertThatCode(() -> webhookService.handle(bytes(STUDENT_UPDATED))).doesNotThrowAnyException();

        verify(webhookEventLogRepository).record("evt_3f9a2c1b8e0d4a7f9c2e1b6d5a4f3c2e", NOW);
    }

    @Test
    @DisplayName("졸업 본문도 예외 없이 처리한다")
    void graduationIsHandled() {
        assertThatCode(() -> webhookService.handle(bytes(GRADUATED))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 처리한 이벤트 ID면 오류 없이 무시한다")
    void duplicateEventIsIgnored() {
        given(webhookEventLogRepository.record(anyString(), any())).willReturn(0);

        assertThatCode(() -> webhookService.handle(bytes(STUDENT_UPDATED))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("처리하지 않는 이벤트는 오류 없이 무시한다")
    void otherEventIsIgnored() {
        String clubUpdated = """
                {"id":"evt_1","event":"club.updated","timestamp":"2026-06-23T05:21:48Z","data":{"new":[]}}
                """;

        assertThatCode(() -> webhookService.handle(bytes(clubUpdated))).doesNotThrowAnyException();
        verify(webhookEventLogRepository, never()).record(anyString(), any());
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
        verify(webhookEventLogRepository, never()).record(anyString(), any());
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    // --- 학생 동기화 ---

    private static final Instant EVENT_TIME = Instant.parse("2026-06-23T05:21:48Z");

    @Test
    @DisplayName("저장된 학생의 이름·학년·반·번호·학번·호실을 반영하고 이벤트 시각을 기록한다")
    void storedStudentIsSynced() {
        Student student = storedStudent(42L, "옛이름", MemberRole.STUDENT, 201);

        handle(EVENT_TIME, studentJson(42, "새이름", 3, 2, 7, 3207, 301, "GENERAL_STUDENT"));

        assertThat(student.getMember().getName()).isEqualTo("새이름");
        assertThat(student.getGrade()).isEqualTo(3);
        assertThat(student.getClassNumber()).isEqualTo(2);
        assertThat(student.getNumber()).isEqualTo(7);
        assertThat(student.getStudentNumber()).isEqualTo(3207);
        assertThat(student.getDormitoryRoom()).isEqualTo(301);
        assertThat(student.getDatagsmSyncedAt()).isEqualTo(EVENT_TIME);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("기숙사 자치위원이 되면 ADMIN, 일반 학생으로 돌아가면 STUDENT가 된다")
    void dormitoryManagerRoleIsSynced() {
        Student student = storedStudent(42L, "학생", MemberRole.STUDENT, 301);

        handle(EVENT_TIME, studentJson(42, "학생", 2, 1, 5, 2105, 301, "DORMITORY_MANAGER"));
        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.ADMIN);

        handle(EVENT_TIME.plusSeconds(60), studentJson(42, "학생", 2, 1, 5, 2105, 301, "GENERAL_STUDENT"));
        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.STUDENT);
    }

    @Test
    @DisplayName("학생회는 관리자가 아니라 STUDENT다")
    void studentCouncilIsStudent() {
        Student student = storedStudent(42L, "학생", MemberRole.ADMIN, 301);

        handle(EVENT_TIME, studentJson(42, "학생", 2, 1, 5, 2105, 301, "STUDENT_COUNCIL"));

        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.STUDENT);
    }

    @Test
    @DisplayName("졸업하면 관리자 권한을 빼고 호실을 비우며 학년·이름은 그대로 두고 떠남 이벤트를 한 번 발행한다")
    void graduationRemovesAdminClearsRoomAndPublishesEvent() {
        Student student = storedStudent(42L, "학생", MemberRole.ADMIN, 301);

        handle(EVENT_TIME, studentJson(42, "학생", null, null, null, null, null, "GRADUATE"));

        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.STUDENT);
        assertThat(student.getMember().getName()).isEqualTo("학생");
        assertThat(student.getGrade()).isEqualTo(2);
        assertThat(student.getDormitoryRoom()).isNull();
        assertThat(student.getDatagsmSyncedAt()).isEqualTo(EVENT_TIME);
        assertThat(publishedLeftEvent()).isEqualTo(new StudentLeftEvent(student.getId(), "GRADUATE"));
    }

    @Test
    @DisplayName("자퇴도 졸업과 같이 관리자 권한을 빼고 호실을 비우며 떠남 이벤트를 발행한다")
    void withdrawalRemovesAdminClearsRoomAndPublishesEvent() {
        Student student = storedStudent(42L, "학생", MemberRole.ADMIN, 301);

        handle(EVENT_TIME, studentJson(42, "학생", null, null, null, null, null, "WITHDRAWN"));

        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.STUDENT);
        assertThat(student.getDormitoryRoom()).isNull();
        assertThat(publishedLeftEvent().reason()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("이미 반영한 졸업 이벤트가 다시 오면 떠남 이벤트를 또 발행하지 않는다")
    void staleGraduationDoesNotPublishAgain() {
        storedStudent(42L, "학생", MemberRole.STUDENT, 301);
        String graduated = studentJson(42, "학생", null, null, null, null, null, "GRADUATE");

        handle(EVENT_TIME, graduated);
        handle(EVENT_TIME, graduated);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("저장되지 않은 학생은 오류 없이 건너뛴다")
    void unknownStudentIsSkipped() {
        assertThatCode(() -> handle(EVENT_TIME,
                studentJson(99, "학생", 2, 1, 5, 2105, 301, "GENERAL_STUDENT")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 반영한 시각과 같거나 이전인 이벤트는 반영하지 않는다")
    void staleEventIsSkipped() {
        Student student = storedStudent(42L, "학생", MemberRole.STUDENT, 301);
        student.markSynced(EVENT_TIME);

        handle(EVENT_TIME, studentJson(42, "학생", 2, 1, 5, 2105, 401, "GENERAL_STUDENT"));
        handle(EVENT_TIME.minusSeconds(60), studentJson(42, "학생", 2, 1, 5, 2105, 201, "GENERAL_STUDENT"));

        assertThat(student.getDormitoryRoom()).isEqualTo(301);
        assertThat(student.getDatagsmSyncedAt()).isEqualTo(EVENT_TIME);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("재학생인데 학년이 없으면 그 학생만 건너뛰고 반영 시각도 남기지 않는다")
    void incompleteStudentIsSkippedOthersAreSynced() {
        Student incomplete = storedStudent(42L, "학생1", MemberRole.STUDENT, 301);
        Student complete = storedStudent(43L, "학생2", MemberRole.STUDENT, 301);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(incomplete, complete));

        handle(EVENT_TIME,
                studentJson(42, "학생1", null, 1, 5, 2105, 401, "GENERAL_STUDENT"),
                studentJson(43, "학생2", 2, 1, 6, 2106, 401, "GENERAL_STUDENT"));

        assertThat(incomplete.getDormitoryRoom()).isEqualTo(301);
        assertThat(incomplete.getDatagsmSyncedAt()).isNull();
        assertThat(complete.getDormitoryRoom()).isEqualTo(401);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("알 수 없는 role이면 권한과 정보를 바꾸지 않는다")
    void unknownRoleIsSkipped() {
        Student student = storedStudent(42L, "학생", MemberRole.ADMIN, 301);

        handle(EVENT_TIME, studentJson(42, "학생", 2, 1, 5, 2105, 401, "SOMETHING_NEW"));

        assertThat(student.getMember().getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(student.getDormitoryRoom()).isEqualTo(301);
        assertThat(student.getDatagsmSyncedAt()).isNull();
    }

    /** DataGSM 학생 ID로 저장된 학생 한 명을 만들고 저장소가 돌려주게 한다. 학년 2, 반 1, 번호 5, 학번 2105. */
    private Student storedStudent(Long datagsmStudentId, String name, MemberRole role, Integer dormitoryRoom) {
        Member member = Member.create(datagsmStudentId + 1000, name, role);
        Student student = Student.create(member, datagsmStudentId, 2, 1, 5, 2105, dormitoryRoom);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(student));
        return student;
    }

    /** 한 번 발행된 떠남 이벤트를 꺼낸다. 발행되지 않았거나 여러 번 발행됐으면 실패한다. */
    private StudentLeftEvent publishedLeftEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (StudentLeftEvent) captor.getValue();
    }

    /** 이벤트 ID가 매번 다른 student.updated 본문으로 처리한다. */
    private void handle(Instant timestamp, String... students) {
        String body = """
                {"id":"evt_%s","event":"student.updated","timestamp":"%s","data":{"new":[%s]}}
                """.formatted(timestamp.toEpochMilli(), timestamp, String.join(",", students));
        webhookService.handle(bytes(body));
    }

    private static String studentJson(int studentId, String name, Integer grade, Integer classNum,
            Integer number, Integer studentNumber, Integer dormitoryRoom, String role) {
        return """
                {"index":0,"object":{"student_id":%d,"name":"%s","grade":%s,"class_num":%s,"number":%s,
                "student_number":%s,"dormitory_room":%s,"role":"%s"}}
                """.formatted(studentId, name, grade, classNum, number, studentNumber, dormitoryRoom, role);
    }
}
