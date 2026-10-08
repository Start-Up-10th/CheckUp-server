package com.checkup.checkup.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.service.StudentIdCache;
import com.checkup.checkup.domain.notification.dto.response.NotificationListResponse;
import com.checkup.checkup.domain.notification.dto.response.NotificationResponse;
import com.checkup.checkup.domain.notification.entity.Notification;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.notification.repository.NotificationRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 알림 서비스가 세션 회원의 학생 알림만 다루고, 학생이 아니면 403으로 막으며,
 * 알림 생성·읽음·폐기에 주입한 시각과 08:00 KST 운영일 경계를 쓰는지 검증한다.
 */
class NotificationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 10L;
    /** 2026-09-30 12:00 KST. 오늘 운영일 시작은 2026-09-30 08:00 KST(= 2026-09-29T23:00Z)다. */
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final StudentIdCache studentIdCache = mock(StudentIdCache.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final NotificationService service = new NotificationService(
            notificationRepository, studentIdCache, clock, new OperatingDayCalculator(clock));

    @Test
    @DisplayName("본인 학생 id로 최근 알림과 미확인 여부를 조회해 응답으로 바꾼다")
    void listUsesOwnStudentId() {
        givenStudent();
        Notification notification = Notification.create(
                null, NotificationType.ATTENDANCE, "STUDY_ROOM:2026-09-30", "자습실 출석이 완료됐어요", NOW);
        ReflectionTestUtils.setField(notification, "id", 5L);
        given(notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(STUDENT_ID))
                .willReturn(List.of(notification));
        given(notificationRepository.existsByStudentIdAndReadAtIsNull(STUDENT_ID)).willReturn(true);

        NotificationListResponse response = service.getNotifications(MEMBER_ID);

        assertThat(response.hasUnread()).isTrue();
        assertThat(response.notifications()).containsExactly(new NotificationResponse(
                5L, NotificationType.ATTENDANCE, "자습실 출석이 완료됐어요", NOW, false));
    }

    @Test
    @DisplayName("미확인 여부는 본인 학생 id로 조회한다")
    void unreadUsesOwnStudentId() {
        givenStudent();
        given(notificationRepository.existsByStudentIdAndReadAtIsNull(STUDENT_ID)).willReturn(false);

        assertThat(service.hasUnread(MEMBER_ID).hasUnread()).isFalse();
    }

    @Test
    @DisplayName("전체 읽음은 본인 학생 id와 현재 시각으로 처리한다")
    void readAllUsesOwnStudentIdAndNow() {
        givenStudent();

        service.readAll(MEMBER_ID);

        verify(notificationRepository).markAllRead(STUDENT_ID, NOW);
    }

    @Test
    @DisplayName("학생 정보가 없는 회원은 403 MISSING_STUDENT_INFO이고 알림을 조회·변경하지 않는다")
    void nonStudentIsRejected() {
        given(studentIdCache.findStudentId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotifications(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
        assertThatThrownBy(() -> service.hasUnread(MEMBER_ID)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.readAll(MEMBER_ID)).isInstanceOf(CustomException.class);
        verify(notificationRepository, never()).findTop50ByStudentIdOrderByCreatedAtDesc(anyLong());
        verify(notificationRepository, never()).markAllRead(anyLong(), any());
    }

    @Test
    @DisplayName("알림은 유형 이름과 현재 시각으로 중복 없이 저장한다")
    void createInsertsWithTypeNameAndNow() {
        service.create(STUDENT_ID, NotificationType.VOLUNTEER, "7", "오늘 봉사 당번이에요");

        verify(notificationRepository).insertIfAbsent(STUDENT_ID, "VOLUNTEER", "7", "오늘 봉사 당번이에요", NOW);
    }

    @Test
    @DisplayName("오늘 운영일 시작(08:00 KST) 전에 만든 출석 알림을 지운다")
    void deleteExpiredAttendanceUsesOperatingDayStart() {
        given(notificationRepository.deleteByTypeBefore(any(), any())).willReturn(3);

        int deleted = service.deleteExpiredAttendance();

        assertThat(deleted).isEqualTo(3);
        verify(notificationRepository).deleteByTypeBefore(
                NotificationType.ATTENDANCE, Instant.parse("2026-09-29T23:00:00Z"));
    }

    @Test
    @DisplayName("08:00 KST 전이면 전날 08:00 KST가 기준이다")
    void deleteBeforeEightUsesPreviousBoundary() {
        clock.setInstant(Instant.parse("2026-09-29T22:59:00Z"));

        service.deleteExpiredAttendance();

        verify(notificationRepository).deleteByTypeBefore(
                NotificationType.ATTENDANCE, Instant.parse("2026-09-28T23:00:00Z"));
    }

    private void givenStudent() {
        given(studentIdCache.findStudentId(MEMBER_ID)).willReturn(Optional.of(STUDENT_ID));
    }
}
