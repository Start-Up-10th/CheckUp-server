package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceFrameResponse;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    private final FaceProperties properties = new FaceProperties(
            "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
            1024, 512, Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
    private final FaceRecognitionService service = new FaceRecognitionService(
            templateRepository, sessionStore, aiFaceClient, studentRepository,
            attendanceService, adminVerifier, properties,
            tools.jackson.databind.json.JsonMapper.builder().build(), clock);

    @Test
    void KNOWN_후보만_현재_출석_대상으로_기록한다() {
        FaceSessionView session = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(1));
                    return frame("KNOWN", DATAGSM_STUDENT_ID.toString());
                });
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDatagsmStudentId()).willReturn(DATAGSM_STUDENT_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(student));
        given(studentRepository.existsByIdAndDormitoryRoomIsNotNull(STUDENT_DB_ID)).willReturn(true);
        given(attendanceService.markAttended(STUDENT_DB_ID, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE))
                .willReturn(AttendanceRecordResult.RECORDED);

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1, 2});

        assertThat(response.faces()).hasSize(1);
        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("KNOWN");
        assertThat(response.faces().getFirst().recognition().studentId()).isEqualTo("900");
        assertThat(response.faces().getFirst().recognition().attendance()).isEqualTo("RECORDED");
        verify(attendanceService).markAttended(STUDENT_DB_ID, AttendancePurpose.DORMITORY, NOW, AttendanceMethod.FACE);
    }

    @Test
    void AI가_현재_세션_후보가_아닌_학생을_반환하면_UNKNOWN으로_낮춘다() {
        FaceSessionView session = session(Set.of(777L));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(frame("KNOWN", DATAGSM_STUDENT_ID.toString()));
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_DB_ID);
        given(student.getDormitoryRoom()).willReturn(301);
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(student));
        given(studentRepository.existsByIdAndDormitoryRoomIsNotNull(777L)).willReturn(true);

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/jpeg", new byte[]{1});

        assertThat(response.faces().getFirst().recognition().status()).isEqualTo("UNKNOWN");
        assertThat(response.faces().getFirst().recognition().studentId()).isNull();
        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
    }

    @Test
    void UNKNOWN과_NOT_ATTEMPTED는_출석으로_기록하지_않는다() {
        FaceSessionView session = session(Set.of(STUDENT_DB_ID));
        given(sessionStore.findOwned(SESSION_ID, ADMIN_ID)).willReturn(session);
        given(studentRepository.existsByIdAndDormitoryRoomIsNotNull(STUDENT_DB_ID)).willReturn(true);
        given(sessionStore.claimFrame(eq(SESSION_ID), eq(ADMIN_ID), any(), any())).willReturn(true);
        given(aiFaceClient.recognize(eq(SESSION_ID), eq("frame-1"), any(), any()))
                .willReturn(new AiFaceFrameResponse("frame-1", List.of(
                        face("UNKNOWN", null), face("NOT_ATTEMPTED", null))));

        var response = service.recognize(ADMIN_ID, SESSION_ID, "frame-1", "image/webp", new byte[]{1});

        assertThat(response.faces()).extracting(f -> f.recognition().status())
                .containsExactly("UNKNOWN", "NOT_ATTEMPTED");
        verify(attendanceService, never()).markAttended(any(), any(), any(), any());
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
