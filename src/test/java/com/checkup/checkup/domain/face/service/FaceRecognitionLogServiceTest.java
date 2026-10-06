package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.dto.FaceRecognitionLogResponse;
import com.checkup.checkup.domain.face.entity.FaceRecognitionLog;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.face.repository.FaceRecognitionLogRepository;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 최근 인식 기록을 08:00 KST 운영일로 남기고, 관리자 본인 세션의 오늘 기록만 최신순으로 돌려주며,
 * 지난 운영일 기록을 지우는지 검증한다(#143, REQ-ATT-007).
 */
class FaceRecognitionLogServiceTest {

    private static final Long ADMIN_ID = 12L;
    private static final UUID SESSION_ID = UUID.randomUUID();
    /** 2026-10-07 07:59 KST. 운영일은 아직 2026-10-06이다. */
    private static final Instant BEFORE_BOUNDARY = Instant.parse("2026-10-06T22:59:00Z");
    private static final LocalDate PREVIOUS_DAY = LocalDate.of(2026, 10, 6);

    private final FaceRecognitionLogRepository repository = mock(FaceRecognitionLogRepository.class);
    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final OperatingDayCalculator calculator =
            new OperatingDayCalculator(Clock.fixed(BEFORE_BOUNDARY, ZoneOffset.UTC));
    private final FaceRecognitionLogService service = new FaceRecognitionLogService(repository, adminVerifier, calculator);

    @Test
    @DisplayName("기록은 인식 시각의 운영일(08:00 KST 경계)과 세션의 관리자·용도로 남긴다")
    void recordUsesOperatingDayOfRecognition() {
        service.record(session(), "track-1", FaceRecognitionResult.SUCCESS, 40L, BEFORE_BOUNDARY);

        verify(repository).insertIfAbsent(SESSION_ID, ADMIN_ID, "DORMITORY", PREVIOUS_DAY, "track-1",
                "SUCCESS", 40L, BEFORE_BOUNDARY);
    }

    @Test
    @DisplayName("128자를 넘는 trackId는 남기지 않는다")
    void tooLongTrackIdIsSkipped() {
        service.record(session(), "t".repeat(129), FaceRecognitionResult.FAILED, null, BEFORE_BOUNDARY);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("관리자 본인의 오늘·용도 기록을 기본 50개 최신순으로 이름·학번과 함께 돌려주고, 못 알아본 얼굴은 이름이 없다")
    void recentLogsAreReturnedForAdminToday() {
        Student student = Student.create(Member.create(100L, "학생", MemberRole.STUDENT), 200L, "학생", 2, 1, 5, 2105, 301);
        given(repository.findByAdminMemberIdAndOperatingDayAndPurposeOrderByRecognizedAtDescIdDesc(
                ADMIN_ID, PREVIOUS_DAY, AttendancePurpose.DORMITORY, Limit.of(50)))
                .willReturn(List.of(
                        log(FaceRecognitionResult.FAILED, null),
                        log(FaceRecognitionResult.SUCCESS, student)));

        List<FaceRecognitionLogResponse> recent = service.getRecent(ADMIN_ID, AttendancePurpose.DORMITORY, null);

        assertThat(recent).containsExactly(
                new FaceRecognitionLogResponse(BEFORE_BOUNDARY, FaceRecognitionResult.FAILED, null, null),
                new FaceRecognitionLogResponse(BEFORE_BOUNDARY, FaceRecognitionResult.SUCCESS, "학생", 2105));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @DisplayName("개수가 1~100을 벗어나면 400 INVALID_REQUEST이고 조회하지 않는다")
    void invalidLimitIsRejected(int limit) {
        assertThatThrownBy(() -> service.getRecent(ADMIN_ID, AttendancePurpose.DORMITORY, limit))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("관리자가 아니면 403 ADMIN_ONLY이고 조회하지 않는다")
    void nonAdminIsRejected() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(ADMIN_ID);

        assertThatThrownBy(() -> service.getRecent(ADMIN_ID, AttendancePurpose.DORMITORY, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("정리는 오늘 운영일보다 앞선 기록을 지운다")
    void deleteExpiredRemovesPreviousDays() {
        given(repository.deleteByOperatingDayBefore(any())).willReturn(3);

        assertThat(service.deleteExpired()).isEqualTo(3);
        verify(repository).deleteByOperatingDayBefore(PREVIOUS_DAY);
    }

    private static FaceSessionView session() {
        return new FaceSessionView(SESSION_ID, ADMIN_ID, AttendancePurpose.DORMITORY, BEFORE_BOUNDARY, true, Set.of(40L));
    }

    private static FaceRecognitionLog log(FaceRecognitionResult result, Student student) {
        FaceRecognitionLog log = BeanUtils.instantiateClass(FaceRecognitionLog.class);
        ReflectionTestUtils.setField(log, "result", result);
        ReflectionTestUtils.setField(log, "student", student);
        ReflectionTestUtils.setField(log, "recognizedAt", BEFORE_BOUNDARY);
        return log;
    }
}
