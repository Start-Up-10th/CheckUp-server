package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceException;
import com.checkup.checkup.domain.face.ai.AiFaceFrameResponse;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.entity.FaceTemplate;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.support.MutableClock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 얼굴 인식이 현재 세션 후보인 KNOWN 학생만 출석으로 기록하고, 후보 밖 학생이나 잘못된 AI 응답으로는 출석을 만들지 않는지,
 * 호실 없는 후보는 프레임마다 쿼리 한 번으로 확인해 세션을 닫는지 검증한다(REQ-FACE-004·005).
 * 프레임 시작 시각과 인증 시각을 분리하고, 시간 계측이 오류·복구·잠금 해제와 원본 폐기를 포함하는지도 검증한다.
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
    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final MockClock metricsClock = new MockClock();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, metricsClock);
    private final FaceProperties properties = new FaceProperties(
            "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
            1024, 512, Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
    private final FaceRecognitionService service = new FaceRecognitionService(
            templateRepository, sessionStore, aiFaceClient, studentRepository,
            attendanceService, adminVerifier, properties,
            tools.jackson.databind.json.JsonMapper.builder().build(), clock, meterRegistry);

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
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID)))
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofMillis(50));
                    return 1L;
                });
        given(attendanceService.markAttended(STUDENT_DB_ID, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        assertThat(response.faces()).hasSize(1);
        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("KNOWN");
        assertThat(response.faces().getFirst().recognition().studentName()).isEqualTo("Student Name");
        assertThat(response.faces().getFirst().recognition().studentNumber()).isEqualTo(15);
        assertThat(response.faces().getFirst().recognition().attendance()).isEqualTo("RECORDED");
        verify(attendanceService).markAttended(STUDENT_DB_ID, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE);
        verify(sessionStore).claimFrame(eq(SESSION_ID), eq(ADMIN_ID), eq(NOW.plusMillis(50)),
                eq(NOW.minusMillis(150)), any(), eq(NOW.plusMillis(92_050)));
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
        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
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
        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
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

        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
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
                    metricsClock.add(Duration.ofSeconds(80));
                    throw new AiFaceException(404, "frame", "SESSION_NOT_FOUND");
                })
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(25));
                    metricsClock.add(Duration.ofSeconds(25));
                    return frame("UNKNOWN", null);
                });
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofSeconds(5));
            return null;
        }).when(aiFaceClient).createSession(eq(SESSION_ID), any());

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1});

        assertThat(response.faces()).hasSize(1);
        verify(sessionStore).extendFrame(eq(SESSION_ID), eq(ADMIN_ID), org.mockito.ArgumentMatchers.any(),
                eq(NOW.plusSeconds(80)), eq(NOW.plusSeconds(206)));
        verify(aiFaceClient).createSession(eq(SESSION_ID), any());
        verify(sessionStore).claimFrame(eq(SESSION_ID), eq(ADMIN_ID), eq(NOW),
                eq(NOW.minusMillis(200)), any(), eq(NOW.plusSeconds(92)));
        assertDuration("ai", "success", 110_000);
        assertDuration("total", "success", 110_000);
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
                eq(List.of(STUDENT_DB_ID)), eq(NOW));
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
    @DisplayName("성공 프레임의 단계별 시간과 잠금 해제를 포함한 전체 시간을 기록한다")
    void recordsStageDurationsThroughRelease() {
        prepareUnknownFrame();
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(5));
            return null;
        }).when(adminVerifier).verify(ADMIN_ID);
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(7));
            return session(Set.of(STUDENT_DB_ID));
        });
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID)))
                .willAnswer(invocation -> {
                    metricsClock.add(Duration.ofMillis(3));
                    return 1L;
                });
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    metricsClock.add(Duration.ofMillis(2));
                    return true;
                });
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willAnswer(invocation -> {
                    metricsClock.add(Duration.ofMillis(40));
                    return frame("UNKNOWN", null);
                });
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(11));
            return null;
        }).when(sessionStore).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
        byte[] image = {1, 2, 3};

        service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image);

        assertDuration("pre_ai", "success", 17);
        assertDuration("ai", "success", 40);
        assertDuration("result", "success", 7);
        assertDuration("release", "success", 11);
        assertDuration("total", "success", 75);
        assertThat(image).containsOnly((byte) 0);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getName()).isEqualTo("checkup.face.frame.duration");
            assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                    .containsExactly("outcome", "stage");
        });
    }

    @Test
    @DisplayName("잠금 해제가 실패해도 시간과 전체 성공을 기록하고 원본을 폐기한다")
    void releaseFailureIsTimedAndImageIsCleared() {
        prepareUnknownFrame();
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(11));
            throw new IllegalStateException("test database unavailable");
        }).when(sessionStore).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
        byte[] image = {1};

        var result = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image);

        assertThat(result.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
        assertDuration("release", "error", 11);
        assertDuration("total", "success", 11);
        assertThat(image).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("계측 등록이 실패해도 AI 결과와 잠금 해제 및 원본 폐기를 유지한다")
    void timingFailureDoesNotInterruptFrameCleanup() {
        prepareUnknownFrame();
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                throw new IllegalStateException("test metrics unavailable");
            }
        });
        byte[] image = {1};

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image);

        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
        verify(sessionStore).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
        assertThat(image).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("처음부터 계측 시계가 실패해도 결과를 반환하고 원본과 프레임 permit을 정리한다")
    void metricsClockFailureAtStartDoesNotInterruptCleanup() {
        prepareUnknownFrame();
        FailingMetricsClock failingClock = new FailingMetricsClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, failingClock);
        try {
            FaceRecognitionService measuredService = serviceWithMetrics(registry);
            failingClock.failing = true;

            for (int attempt = 0; attempt < properties.maxConcurrentFrames() + 1; attempt++) {
                byte[] image = {1};
                var response = measuredService.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image);

                assertThat(response.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
                assertThat(image).containsOnly((byte) 0);
            }
        } finally {
            registry.close();
        }
        verify(sessionStore, times(3)).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
    }

    @Test
    @DisplayName("결과 처리 뒤 계측 시계가 실패해도 잠금 해제 시작과 원본 및 permit 정리를 유지한다")
    void metricsClockFailureBeforeReleaseDoesNotInterruptCleanup() {
        prepareUnknownFrame();
        FailingMetricsClock failingClock = new FailingMetricsClock();
        AtomicInteger sessionReads = new AtomicInteger();
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willAnswer(invocation -> {
            if (sessionReads.incrementAndGet() % 2 == 0) {
                failingClock.failing = true;
            }
            return session(Set.of(STUDENT_DB_ID));
        });
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, failingClock);
        try {
            FaceRecognitionService measuredService = serviceWithMetrics(registry);

            for (int attempt = 0; attempt < properties.maxConcurrentFrames() + 1; attempt++) {
                failingClock.failing = false;
                byte[] image = {1};
                var response = measuredService.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image);

                assertThat(response.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
                assertThat(image).containsOnly((byte) 0);
            }
        } finally {
            registry.close();
        }
        verify(sessionStore, times(3)).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
    }

    @Test
    @DisplayName("AI 오류 뒤 세션 정리와 잠금 해제를 전체 시간에 포함한다")
    void aiFailureCleanupIsIncludedInTotalDuration() {
        prepareUnknownFrame();
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willAnswer(invocation -> {
                    metricsClock.add(Duration.ofMillis(40));
                    throw new AiFaceException(504, "frame", "timeout");
                });
        given(sessionStore.findOwnedIfPresent(SESSION_ID, ADMIN_ID))
                .willReturn(Optional.of(session(Set.of(STUDENT_DB_ID))));
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(7));
            return null;
        }).when(aiFaceClient).deleteSession(SESSION_ID);
        doAnswer(invocation -> {
            metricsClock.add(Duration.ofMillis(11));
            return null;
        }).when(sessionStore).releaseFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any());
        byte[] image = {1};

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image))
                .isInstanceOf(AiFaceException.class);

        assertDuration("ai", "error", 47);
        assertDuration("release", "success", 11);
        assertDuration("total", "error", 58);
        verify(sessionStore).markInactive(SESSION_ID, ADMIN_ID);
        verify(sessionStore).delete(SESSION_ID);
        assertThat(image).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("프레임 제한으로 거절하면 AI와 잠금 해제를 호출하지 않고 시간을 기록한다")
    void rateLimitedFrameIsTimedWithoutInference() {
        prepareUnknownFrame();
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any()))
                .willReturn(false);
        byte[] image = {1};

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", image))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FACE_FRAME_RATE_LIMITED));

        assertDuration("pre_ai", "error", 0);
        assertDuration("total", "rate_limited", 0);
        assertThat(meterRegistry.find("checkup.face.frame.duration").tag("stage", "ai").timer()).isNull();
        verify(aiFaceClient, never()).recognize(any(), any(), any(), any());
        verify(sessionStore, never()).releaseFrame(any(), any(), any(), any());
        assertThat(image).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("AI 처리 중 세션이 닫히면 반환된 KNOWN으로 출석하지 않는다")
    void closedSessionAfterInferenceRecordsNoAttendance() {
        prepareUnknownFrame();
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID))
                .willReturn(session(Set.of(STUDENT_DB_ID)))
                .willThrow(new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND));
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1}))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FACE_SESSION_NOT_FOUND));

        verify(aiFaceClient).deleteSession(SESSION_ID);
        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
        assertDuration("result", "error", 0);
        assertDuration("total", "error", 0);
    }

    private void prepareUnknownFrame() {
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session(Set.of(STUDENT_DB_ID)));
        given(studentRepository.countByIdInAndDormitoryRoomIsNotNull(Set.of(STUDENT_DB_ID))).willReturn(1L);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any(), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(frame("UNKNOWN", null));
    }

    private FaceRecognitionService serviceWithMetrics(SimpleMeterRegistry registry) {
        return new FaceRecognitionService(templateRepository, sessionStore, aiFaceClient, studentRepository,
                attendanceService, adminVerifier, properties,
                tools.jackson.databind.json.JsonMapper.builder().build(), clock, registry);
    }

    private static final class FailingMetricsClock implements io.micrometer.core.instrument.Clock {
        private boolean failing;

        @Override
        public long wallTime() {
            return 0;
        }

        @Override
        public long monotonicTime() {
            if (failing) {
                throw new IllegalStateException("test metrics clock unavailable");
            }
            return 0;
        }
    }

    private void assertDuration(String stage, String outcome, double milliseconds) {
        Timer timer = meterRegistry.find("checkup.face.frame.duration")
                .tags("stage", stage, "outcome", outcome).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(milliseconds);
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
