package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceException;
import com.checkup.checkup.domain.face.ai.AiFaceFrameResponse;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.entity.FaceTemplate;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 얼굴 인식이 현재 세션 후보인 KNOWN 학생만 출석으로 기록하고, 후보 밖 학생이나 잘못된 AI 응답으로는 출석을 만들지 않는지,
 * 호실 없는 후보는 프레임마다 쿼리 한 번으로 확인해 세션을 닫는지 검증한다.
 */
class FaceRecognitionServiceTest {
    private static final Long ADMIN_ID = 12L;
    private static final Long STUDENT_DB_ID = 40L;
    private static final Long DATAGSM_STUDENT_ID = 900L;
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private final FaceTemplateRepository templateRepository = mock(FaceTemplateRepository.class);
    private final FaceSessionStore sessionStore = mock(FaceSessionStore.class);
    private final AiFaceClient aiFaceClient = mock(AiFaceClient.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final FaceRecognitionLogService logService = mock(FaceRecognitionLogService.class);
    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final MutableClock clock = new MutableClock(NOW);
    /** {@link #givenEligibleStudent}가 만든 학생. */
    private Student eligibleStudent;
    private final FaceProperties properties = new FaceProperties(
            "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
            1024, 512, Duration.ofMillis(200), 2, 2, Duration.ofSeconds(1), Duration.ofMinutes(5), 60_000, "v1");
    private final FaceRecognitionService service = new FaceRecognitionService(
            templateRepository, sessionStore, aiFaceClient, studentRepository,
            attendanceService, logService, adminVerifier, properties,
            tools.jackson.databind.json.JsonMapper.builder().build(), clock);

    @BeforeEach
    void sessionStaysActiveDuringAiCall() {
        given(sessionStore.isOwnedActive(SESSION_ID, ADMIN_ID)).willReturn(true);
    }

    @Test
    @DisplayName("KNOWN 후보만 현재 출석 대상으로 기록한다")
    void recordsOnlyKnownCandidates() {
        FaceSessionView session = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(1));
                    return frame("KNOWN", DATAGSM_STUDENT_ID.toString());
                });
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDatagsmStudentId()).willReturn(DATAGSM_STUDENT_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(student.isAttendanceEligible()).willReturn(true);
        given(student.getName()).willReturn("Student Name");
        given(student.getStudentNumber()).willReturn(15);
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(student));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(attendanceService.markAttendedFor(student, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        assertThat(response.faces()).hasSize(1);
        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("KNOWN");
        assertThat(response.faces().getFirst().recognition().studentName()).isEqualTo("Student Name");
        assertThat(response.faces().getFirst().recognition().studentNumber()).isEqualTo(15);
        assertThat(response.faces().getFirst().recognition().attendance()).isEqualTo("RECORDED");
        verify(attendanceService).markAttendedFor(student, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE);
    }

    @Test
    @DisplayName("AI가 현재 세션 후보가 아닌 학생을 반환하면 UNKNOWN으로 낮춘다")
    void nonCandidateFromAiBecomesUnknown() {
        FaceSessionView session = session(Set.of(777L));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(student.isAttendanceEligible()).willReturn(true);
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(student));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(777L))).willReturn(1L);

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1});

        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
        assertThat(response.faces().getFirst().recognition().studentName()).isNull();
        verify(attendanceService, never()).markAttendedFor(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UNKNOWN과 NOT_ATTEMPTED는 출석으로 기록하지 않는다")
    void unknownAndNotAttemptedAreNotRecorded() {
        FaceSessionView session = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(new AiFaceFrameResponse("frame-1", List.of(
                        face("UNKNOWN", null), face("NOT_ATTEMPTED", null))));

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/webp", new byte[]{1});

        assertThat(response.faces()).extracting(f -> f.recognition().status())
                .containsExactly("UNKNOWN", "NOT_ATTEMPTED");
        verify(attendanceService, never()).markAttendedFor(any(), any(), any(), any());
    }

    @Test
    @DisplayName("얼굴 하나라도 응답 검증에 실패하면 어떤 얼굴도 출석 처리하지 않는다")
    void anyInvalidFaceRecordsNoAttendance() {
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session(Set.of(STUDENT_DB_ID)));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        AiFaceFrameResponse.FaceResult invalid = new AiFaceFrameResponse.FaceResult(
                "track-2", List.of(0.95, 0.2, 0.2, 0.4), List.of(List.of(4.0, 5.0)),
                new AiFaceFrameResponse.Quality(0.8, 0.7, List.of()),
                new AiFaceFrameResponse.Recognition("UNKNOWN", null, null, null), 0, false);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(new AiFaceFrameResponse("frame-1",
                        List.of(face("KNOWN", DATAGSM_STUDENT_ID.toString()), invalid)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1}))
                .isInstanceOfSatisfying(com.checkup.checkup.global.exception.CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(
                                com.checkup.checkup.global.exception.ErrorCode.FACE_AI_BAD_GATEWAY));

        verify(attendanceService, never()).markAttendedFor(any(), any(), any(), any());
    }

    @Test
    @DisplayName("복구가 원래 잠금 시간보다 길어도 프레임 잠금을 연장한다")
    void recoveryExtendsFrameLock() throws Exception {
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session(Set.of(STUDENT_DB_ID)));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(sessionStore.extendFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any())).willReturn(true);
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDatagsmStudentId()).willReturn(DATAGSM_STUDENT_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(student.isAttendanceEligible()).willReturn(true);
        given(student.hasRequiredConsent()).willReturn(true);
        FaceTemplate template = mock(FaceTemplate.class);
        given(template.getStudent()).willReturn(student);
        given(template.getModelId()).willReturn("model-a");
        given(template.getModelVersion()).willReturn("v1");
        given(template.getDimension()).willReturn(256);
        given(template.getNormalization()).willReturn("l2");
        given(template.getVectorsJson()).willReturn(toVectorsJson());
        given(templateRepository.findAllByStudent_IdIn(Set.of(STUDENT_DB_ID))).willReturn(List.of(template));
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(80));
                    throw new AiFaceException(404, "frame", "SESSION_NOT_FOUND");
                })
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(25));
                    return frame("UNKNOWN", null);
                });

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1});

        assertThat(response.faces()).hasSize(1);
        verify(sessionStore).extendFrame(eq(SESSION_ID), eq(ADMIN_ID), org.mockito.ArgumentMatchers.any(),
                eq(NOW.plusSeconds(80)), eq(NOW.plusSeconds(206)));
        verify(aiFaceClient).createSession(eq(SESSION_ID), any());
    }

    @Test
    @DisplayName("AI 세션 생성 전에 DB 소유권을 저장해 삭제 실패를 재시도 대기열에 남긴다")
    void ownershipIsSavedBeforeAiSessionForCleanupRetry() throws Exception {
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDatagsmStudentId()).willReturn(DATAGSM_STUDENT_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(student.isAttendanceEligible()).willReturn(true);
        given(student.hasRequiredConsent()).willReturn(true);
        FaceTemplate template = mock(FaceTemplate.class);
        given(template.getStudent()).willReturn(student);
        given(template.getModelId()).willReturn("model-a");
        given(template.getModelVersion()).willReturn("v1");
        given(template.getDimension()).willReturn(256);
        given(template.getNormalization()).willReturn("l2");
        given(template.getVectorsJson()).willReturn(toVectorsJson());
        given(templateRepository.findAllByOrderByStudent_IdAsc()).willReturn(List.of(template));
        FaceSessionView pending = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwnedIfPresent(any(), eq(ADMIN_ID))).willReturn(java.util.Optional.of(pending));
        org.mockito.BDDMockito.willThrow(new AiFaceException(503, "session_create", "unavailable"))
                .given(aiFaceClient).createSession(any(), any());
        org.mockito.BDDMockito.willThrow(new AiFaceException(503, "session_delete", "unavailable"))
                .given(aiFaceClient).deleteSession(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(ADMIN_ID,
                com.checkup.checkup.domain.attendance.entity.AttendancePurpose.DORMITORY))
                .isInstanceOf(AiFaceException.class);

        var order = inOrder(sessionStore, aiFaceClient);
        order.verify(sessionStore).create(any(), eq(ADMIN_ID),
                eq(com.checkup.checkup.domain.attendance.entity.AttendancePurpose.DORMITORY),
                eq(List.of(STUDENT_DB_ID)), any(), any());
        order.verify(aiFaceClient).createSession(any(), any());
        verify(sessionStore).markInactive(any(), eq(ADMIN_ID));
        verify(sessionStore, never()).delete(any());
    }

    private static String toVectorsJson() throws Exception {
        List<Double> vector = new java.util.ArrayList<>(java.util.Collections.nCopies(256, 0.0));
        vector.set(0, 1.0);
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(List.of(vector));
    }

    @Test
    @DisplayName("호실이 비었거나 저장되지 않은 후보가 있으면 쿼리 한 번으로 확인해 세션을 닫고 AI를 부르지 않는다")
    void candidateWithoutRoomClosesSession() {
        Set<Long> candidates = Set.of(STUDENT_DB_ID, 41L);
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session(candidates));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(candidates)).willReturn(1L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1}))
                .isInstanceOfSatisfying(com.checkup.checkup.global.exception.CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(
                                com.checkup.checkup.global.exception.ErrorCode.FACE_SESSION_NOT_FOUND));

        verify(studentRepository).countByIdInAndDormitoryRoomIsNotNull(candidates);
        verify(sessionStore).markInactive(SESSION_ID, ADMIN_ID);
        verify(aiFaceClient, never()).recognize(any(), any(), any(), any());
    }

    @Test
    @DisplayName("알아보고 출석 처리한 얼굴은 최근 인식에 학생과 함께 SUCCESS로 남긴다")
    void recordedFaceIsLoggedAsSuccess() {
        FaceSessionView session = givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(attendanceService.markAttendedFor(eligibleStudent, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        verify(logService).record(session, "track-1", FaceRecognitionResult.SUCCESS, STUDENT_DB_ID, NOW);
    }

    @Test
    @DisplayName("알아봤지만 늦게 도착해 출석 처리되지 않은 얼굴은 학생과 함께 FAILED로 남긴다")
    void staleFaceIsLoggedAsFailed() {
        FaceSessionView session = givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(attendanceService.markAttendedFor(eligibleStudent, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.STALE);

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        verify(logService).record(session, "track-1", FaceRecognitionResult.FAILED, STUDENT_DB_ID, NOW);
    }

    @Test
    @DisplayName("이미 출석한 학생은 최근 인식에 남기지 않는다")
    void duplicateFaceIsNotLogged() {
        givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(attendanceService.markAttendedFor(eligibleStudent, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.ALREADY_ATTENDED);

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        verifyNoInteractions(logService);
    }

    @Test
    @DisplayName("못 알아본 얼굴은 AI가 QR을 안내할 때만 학생 없이 FAILED로 남긴다")
    void unknownFaceIsLoggedOnlyWhenQrRecommended() {
        FaceSessionView session = givenFrame(frame("UNKNOWN", null));

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});
        verifyNoInteractions(logService);

        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(new AiFaceFrameResponse("frame-1", List.of(qrRecommendedFace())));
        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        verify(logService).record(session, "track-1", FaceRecognitionResult.FAILED, null, NOW);
    }

    @Test
    @DisplayName("프레임 하나에 세션·후보는 한 번만 읽고, AI 응답 뒤에는 세션이 열려 있는지만 확인한다")
    void readsSessionOncePerFrame() {
        givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(attendanceService.markAttendedFor(eligibleStudent, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        verify(sessionStore, times(1)).findOwned(SESSION_ID, ADMIN_ID);
        verify(sessionStore, times(1)).isOwnedActive(SESSION_ID, ADMIN_ID);
        verify(studentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("AI를 기다리는 동안 세션이 닫히면 출석을 기록하지 않고 404 FACE_SESSION_NOT_FOUND다")
    void sessionClosedDuringAiCallIsNotRecorded() {
        givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(sessionStore.isOwnedActive(SESSION_ID, ADMIN_ID)).willReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2}))
                .isInstanceOfSatisfying(com.checkup.checkup.global.exception.CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(
                                com.checkup.checkup.global.exception.ErrorCode.FACE_SESSION_NOT_FOUND));

        verify(attendanceService, never()).markAttendedFor(any(), any(), any(), any());
        verify(aiFaceClient).deleteSession(SESSION_ID);
    }

    @Test
    @DisplayName("최근 인식 기록에 실패해도 인식·출석 응답은 그대로 돌려준다")
    void logFailureDoesNotBreakRecognition() {
        givenFrame(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        givenEligibleStudent();
        given(attendanceService.markAttendedFor(eligibleStudent, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);
        willThrow(new IllegalStateException("db down")).given(logService).record(any(), any(), any(), any(), any());

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        assertThat(response.faces().getFirst().recognition().attendance()).isEqualTo("RECORDED");
    }

    /** 현재 세션 후보가 학생 한 명이고, 프레임을 보내면 AI가 주어진 결과를 돌려주게 한다. */
    private FaceSessionView givenFrame(AiFaceFrameResponse frame) {
        FaceSessionView session = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any())).willReturn(frame);
        return session;
    }

    private void givenEligibleStudent() {
        Student student = mock(Student.class);
        eligibleStudent = student;
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.isAttendanceEligible()).willReturn(true);
        given(student.getName()).willReturn("Student Name");
        given(student.getStudentNumber()).willReturn(15);
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(student));
    }

    private static AiFaceFrameResponse.FaceResult qrRecommendedFace() {
        return new AiFaceFrameResponse.FaceResult("track-1", List.of(0.1, 0.2, 0.3, 0.4),
                List.of(List.of(0.2, 0.3)), new AiFaceFrameResponse.Quality(0.8, 0.7, List.of()),
                new AiFaceFrameResponse.Recognition("UNKNOWN", null, 0.31, 0.02), 3, true);
    }

    private static FaceSessionView session(Set<Long> candidates) {
        return new FaceSessionView(SESSION_ID, ADMIN_ID, AttendancePurpose.DORMITORY,
                NOW.minusSeconds(2), true, candidates);
    }

    private static AiFaceFrameResponse frame(String status, String studentId) {
        return new AiFaceFrameResponse("frame-1", List.of(face(status, studentId)));
    }

    private static AiFaceFrameResponse.FaceResult face(String status, String studentId) {
        return new AiFaceFrameResponse.FaceResult("track-1", List.of(0.1, 0.2, 0.3, 0.4),
                List.of(List.of(0.2, 0.3)), new AiFaceFrameResponse.Quality(0.8, 0.7, List.of()),
                new AiFaceFrameResponse.Recognition(status, studentId, 0.91, 0.12), 0, false);
    }
}
