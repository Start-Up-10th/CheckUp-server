package com.checkup.checkup.domain.volunteer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import com.checkup.checkup.domain.notification.service.NotificationService;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.volunteer.repository.VolunteerAdjustmentRepository;
import com.checkup.checkup.domain.volunteer.repository.VolunteerDutyRepository;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 봉사 관리 명단이 관리자에게만 전체 학생을 봉사 횟수와 함께 보여주고,
 * 횟수 조정이 남은 횟수까지만 빼며 사유·요청 횟수를 기록하고 재시도를 한 번만 반영하는지 검증한다(#139).
 */
class VolunteerServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");

    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final VolunteerAdjustmentRepository volunteerAdjustmentRepository = mock(VolunteerAdjustmentRepository.class);
    private final VolunteerDutyRepository volunteerDutyRepository = mock(VolunteerDutyRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final VolunteerService service = new VolunteerService(
            adminVerifier, studentRepository, volunteerAdjustmentRepository, volunteerDutyRepository,
            notificationService, new OperatingDayCalculator(clock), clock);

    @Test
    @DisplayName("전체 학생을 DataGSM id·이름·학번·호실·봉사 횟수로 응답하고 최근 활동은 비운다")
    void listIsConverted() {
        Student student = Student.create(Member.create(100L, "학생", MemberRole.STUDENT), 200L, "학생", 2, 1, 5, 2105, 301);
        ReflectionTestUtils.setField(student, "volunteerCount", 2);
        given(studentRepository.findAllByDatagsmStudentIdIsNotNull()).willReturn(List.of(student));

        List<VolunteerResponse> list = service.getVolunteers(MEMBER_ID, null, null, null, null);

        assertThat(list).containsExactly(new VolunteerResponse(200L, "학생", 2105, 301, 2, null, null));
    }

    @Test
    @DisplayName("호실 → 이름 → 학번순으로 정렬하고 호실이 없는 학생은 맨 뒤에 둔다")
    void sortedByRoomThenNameThenNumber() {
        givenStudents(
                student(1L, "나학생", 2102, 412),
                student(2L, "가학생", 2101, null),
                student(3L, "다학생", 2103, 301),
                student(4L, "가학생", 2104, 412));

        assertThat(service.getVolunteers(MEMBER_ID, null, null, null, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2103, 2104, 2102, 2101);
    }

    @Test
    @DisplayName("층을 고르면 호실 맨 앞자리가 그 층인 학생만 나오고 호실이 없는 학생은 빠진다")
    void filteredByFloor() {
        givenStudents(
                student(1L, "학생1", 2101, 412),
                student(2L, "학생2", 2102, 501),
                student(3L, "학생3", 2103, null),
                student(4L, "학생4", 2104, 401));

        assertThat(service.getVolunteers(MEMBER_ID, 4, null, null, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2104, 2101);
    }

    @Test
    @DisplayName("숫자로 검색하면 호실 번호나 학번이 정확히 같은 학생만 나온다")
    void numberSearchMatchesRoomOrStudentNumberExactly() {
        givenStudents(
                student(1L, "학생1", 2101, 412),
                student(2L, "학생2", 2102, 412),
                student(3L, "학생3", 2103, 413),
                student(4L, "학생4", 4120, 301));

        assertThat(service.getVolunteers(MEMBER_ID, null, " 412 ", null, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2101, 2102);
        assertThat(service.getVolunteers(MEMBER_ID, null, "2103", null, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2103);
    }

    @Test
    @DisplayName("글자로 검색하면 이름에 포함된 학생만 나오고, 층 필터와 함께 쓸 수 있다")
    void nameSearchWithFloor() {
        givenStudents(
                student(1L, "강민우", 2101, 412),
                student(2L, "김민우", 2102, 501),
                student(3L, "홍길동", 2103, 413));

        assertThat(service.getVolunteers(MEMBER_ID, null, "민우", null, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민우", "김민우");
        assertThat(service.getVolunteers(MEMBER_ID, 4, "민우", null, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민우");
    }

    @Test
    @DisplayName("초성과 오타 1개로도 찾고, 이름 포함 → 초성 → 오타 순으로 앞에 둔다")
    void similarNameSearchIsRankedBeforeRoomOrder() {
        givenStudents(
                student(1L, "강민오", 2101, 301),
                student(2L, "강민우", 2102, 501),
                student(3L, "김민우", 2103, 401),
                student(4L, "홍길동", 2104, 201));

        assertThat(service.getVolunteers(MEMBER_ID, null, "강민우", null, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민우", "강민오");
        assertThat(service.getVolunteers(MEMBER_ID, null, "ㄱㅁㅇ", null, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민오", "김민우", "강민우");
    }

    @Test
    @DisplayName("최소 봉사 횟수를 주면 그 이상 남은 학생만 나오고 검색과 함께 쓸 수 있다")
    void filteredByMinCountWithSearch() {
        Student none = student(1L, "강민우", 2101, 301);
        Student one = student(2L, "김민우", 2102, 401);
        Student two = student(3L, "홍길동", 2103, 402);
        ReflectionTestUtils.setField(one, "volunteerCount", 1);
        ReflectionTestUtils.setField(two, "volunteerCount", 2);
        givenStudents(none, one, two);

        assertThat(service.getVolunteers(MEMBER_ID, null, null, 1, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("김민우", "홍길동");
        assertThat(service.getVolunteers(MEMBER_ID, null, "ㄱㅁㅇ", 1, null))
                .extracting(VolunteerResponse::name)
                .containsExactly("김민우");
    }

    @Test
    @DisplayName("명단의 최근 활동은 학생별 마지막 조정 시각이다")
    void listFillsLastActivity() {
        Student student = student(1L, "학생", 2101, 301);
        ReflectionTestUtils.setField(student, "id", 10L);
        givenStudents(student);
        given(volunteerAdjustmentRepository.findLastActivities()).willReturn(List.of(lastActivity(10L, NOW)));

        assertThat(service.getVolunteers(MEMBER_ID, null, null, null, null))
                .extracting(VolunteerResponse::lastActivityAt)
                .containsExactly(NOW);
    }

    @Test
    @DisplayName("증가는 기록을 남긴 뒤 횟수를 1 늘리고 바뀐 항목을 돌려준다")
    void increaseRecordsThenIncreases() {
        Student student = givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, 1, 1, null, "key-1", NOW)).willReturn(1);
        given(studentRepository.changeVolunteerCount(10L, 1)).willReturn(1);
        given(volunteerAdjustmentRepository.findLastActivityAt(10L)).willReturn(NOW);

        VolunteerResponse response = service.increase(MEMBER_ID, 200L, " key-1 ");

        verify(studentRepository).changeVolunteerCount(10L, 1);
        assertThat(response.studentId()).isEqualTo(200L);
        assertThat(response.lastActivityAt()).isEqualTo(NOW);
        assertThat(response.volunteerCount()).isEqualTo(student.getVolunteerCount());
    }

    @Test
    @DisplayName("같은 키로 다시 오면 횟수를 바꾸지 않고 현재 상태만 돌려준다")
    void retryWithSameKeyDoesNotChangeCount() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, 1, 1, null, "key-1", NOW)).willReturn(0);
        given(volunteerAdjustmentRepository.findByRequestKey("key-1")).willReturn(Optional.of(adjustment(10L, 1)));

        service.increase(MEMBER_ID, 200L, "key-1");

        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("같은 키가 다른 학생이나 반대 방향 요청에 쓰였으면 409 IDEMPOTENCY_KEY_REUSED다")
    void reusedKeyForOtherRequestIsRejected() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any())).willReturn(0);

        given(volunteerAdjustmentRepository.findByRequestKey("key-1")).willReturn(Optional.of(adjustment(99L, 1)));
        assertThatThrownBy(() -> service.increase(MEMBER_ID, 200L, "key-1"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        given(volunteerAdjustmentRepository.findByRequestKey("key-1")).willReturn(Optional.of(adjustment(10L, 1)));
        assertThatThrownBy(() -> service.decrease(MEMBER_ID, 200L, "key-1"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("키가 없거나 비어 있으면 null로 기록하고 매번 반영한다")
    void blankKeyIsNull() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, 1, 1, null, null, NOW)).willReturn(1);
        given(studentRepository.changeVolunteerCount(10L, 1)).willReturn(1);

        service.increase(MEMBER_ID, 200L, "  ");

        verify(volunteerAdjustmentRepository).insertIfAbsent(10L, 1, 1, null, null, NOW);
        verify(studentRepository).changeVolunteerCount(10L, 1);
    }

    @Test
    @DisplayName("100자를 넘는 키는 400 INVALID_REQUEST이고 기록하지 않는다")
    void tooLongKeyIsRejected() {
        givenAdjustable(2);

        assertThatThrownBy(() -> service.increase(MEMBER_ID, 200L, "k".repeat(101)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("차감은 -1로 기록하고 횟수를 1 줄인다")
    void decreaseRecordsThenDecreases() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, -1, -1, null, null, NOW)).willReturn(1);
        given(studentRepository.changeVolunteerCount(10L, -1)).willReturn(1);

        service.decrease(MEMBER_ID, 200L, null);

        verify(studentRepository).changeVolunteerCount(10L, -1);
    }

    @Test
    @DisplayName("횟수가 0이면 409 VOLUNTEER_COUNT_ZERO다")
    void decreaseAtZeroIsRejected() {
        givenAdjustable(0);

        assertThatThrownBy(() -> service.decrease(MEMBER_ID, 200L, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VOLUNTEER_COUNT_ZERO));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("여러 회 추가는 요청한 횟수와 다듬은 사유로 기록하고 그만큼 늘린다")
    void adjustCountIncreasesByDeltaWithReason() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, 5, 5, "벌점", "key-1", NOW)).willReturn(1);

        service.adjustCount(MEMBER_ID, 200L, "key-1", 5, "  벌점 ");

        verify(studentRepository).changeVolunteerCount(10L, 5);
    }

    @Test
    @DisplayName("남은 횟수보다 많이 빼면 남은 횟수까지만 빼고 요청 횟수는 따로 기록한다")
    void adjustCountClampsDecreaseToRemaining() {
        givenAdjustable(2);
        given(volunteerAdjustmentRepository.insertIfAbsent(10L, -2, -5, null, null, NOW)).willReturn(1);

        service.adjustCount(MEMBER_ID, 200L, null, -5, " ");

        verify(volunteerAdjustmentRepository).insertIfAbsent(10L, -2, -5, null, null, NOW);
        verify(studentRepository).changeVolunteerCount(10L, -2);
    }

    @Test
    @DisplayName("남은 횟수가 0이면 여러 회 차감도 409 VOLUNTEER_COUNT_ZERO이고 기록하지 않는다")
    void adjustCountDecreaseAtZeroIsRejected() {
        givenAdjustable(0);

        assertThatThrownBy(() -> service.adjustCount(MEMBER_ID, 200L, "key-1", -3, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VOLUNTEER_COUNT_ZERO));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("차감으로 0이 된 뒤 같은 키로 재시도하면 409 없이 현재 상태를 돌려준다")
    void adjustCountRetryAfterReachingZeroSucceeds() {
        givenAdjustable(0);
        given(volunteerAdjustmentRepository.findByRequestKey("key-1"))
                .willReturn(Optional.of(adjustment(10L, -2, -5)));

        service.adjustCount(MEMBER_ID, 200L, "key-1", -5, null);

        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("같은 키를 요청 횟수가 다른 조정에 다시 쓰면 409 IDEMPOTENCY_KEY_REUSED다")
    void adjustCountReusedKeyWithOtherDeltaIsRejected() {
        givenAdjustable(5);
        given(volunteerAdjustmentRepository.insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(0);
        given(volunteerAdjustmentRepository.findByRequestKey("key-1"))
                .willReturn(Optional.of(adjustment(10L, 3, 3)));

        assertThatThrownBy(() -> service.adjustCount(MEMBER_ID, 200L, "key-1", 4, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(studentRepository, never()).changeVolunteerCount(anyLong(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, -100})
    @DisplayName("여러 회 조정 횟수가 0이거나 -99~99를 벗어나면 400 INVALID_REQUEST이고 기록하지 않는다")
    void adjustCountRejectsInvalidDelta(int delta) {
        givenAdjustable(5);

        assertThatThrownBy(() -> service.adjustCount(MEMBER_ID, 200L, null, delta, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("다듬은 사유가 100자를 넘으면 400 INVALID_REQUEST이고 기록하지 않는다")
    void adjustCountRejectsTooLongReason() {
        givenAdjustable(5);

        assertThatThrownBy(() -> service.adjustCount(MEMBER_ID, 200L, null, 1, "가".repeat(101)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("저장된 학생이 없으면 404 STUDENT_NOT_FOUND이고 기록하지 않는다")
    void adjustUnknownStudentIsRejected() {
        given(studentRepository.findByDatagsmStudentId(200L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.increase(MEMBER_ID, 200L, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("명단에 오늘 지정 상태를 채우고, 오늘 봉사자만 보기로 지정된 학생만 거른다")
    void listFillsTodayDutyAndFiltersOnDuty() {
        Student assigned = student(1L, "지정", 2101, 301);
        Student free = student(2L, "미지정", 2102, 302);
        ReflectionTestUtils.setField(assigned, "id", 11L);
        ReflectionTestUtils.setField(free, "id", 12L);
        givenStudents(assigned, free);
        VolunteerDuty duty = BeanUtils.instantiateClass(VolunteerDuty.class);
        ReflectionTestUtils.setField(duty, "student", assigned);
        ReflectionTestUtils.setField(duty, "status", DutyStatus.ASSIGNED);
        given(volunteerDutyRepository.findAllByOperatingDay(TODAY)).willReturn(List.of(duty));

        assertThat(service.getVolunteers(MEMBER_ID, null, null, null, null))
                .extracting(VolunteerResponse::todayDuty)
                .containsExactly(DutyStatus.ASSIGNED, null);
        assertThat(service.getVolunteers(MEMBER_ID, null, null, null, true))
                .extracting(VolunteerResponse::name)
                .containsExactly("지정");
    }

    @Test
    @DisplayName("봉사가 남은 학생을 오늘 봉사자로 지정하고 학생·날짜별 봉사 알림을 만든다")
    void assignCreatesDutyAndNotification() {
        givenAdjustable(2);
        given(volunteerDutyRepository.assign(10L, TODAY, NOW)).willReturn(1);

        service.assignDuty(MEMBER_ID, 200L);

        verify(notificationService).create(eq(10L), eq(NotificationType.VOLUNTEER), eq("duty:2026-10-01"), any());
    }

    @Test
    @DisplayName("봉사 횟수가 0이면 409 NO_VOLUNTEER_LEFT이고 지정·알림을 만들지 않는다")
    void assignWithoutVolunteerIsRejected() {
        givenAdjustable(0);

        assertThatThrownBy(() -> service.assignDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NO_VOLUNTEER_LEFT));
        verify(volunteerDutyRepository, never()).assign(anyLong(), any(), any());
        verify(notificationService, never()).create(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("오늘 이미 지정됐으면 409 ALREADY_ON_DUTY이고 알림을 또 만들지 않는다")
    void assignTwiceIsRejected() {
        givenAdjustable(2);
        given(volunteerDutyRepository.assign(10L, TODAY, NOW)).willReturn(0);

        assertThatThrownBy(() -> service.assignDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_ON_DUTY));
        verify(notificationService, never()).create(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("지정을 취소하면 그 학생·날짜의 봉사 알림을 지운다")
    void cancelDeletesNotification() {
        givenAdjustable(2);
        givenTodayDuty(DutyStatus.ASSIGNED);
        given(volunteerDutyRepository.cancel(50L)).willReturn(1);

        service.cancelDuty(MEMBER_ID, 200L);

        verify(notificationService).delete(10L, NotificationType.VOLUNTEER, "duty:2026-10-01");
    }

    @Test
    @DisplayName("오늘 지정되지 않았으면 취소는 404 NOT_ON_DUTY다")
    void cancelWithoutDutyIsRejected() {
        givenAdjustable(2);

        assertThatThrownBy(() -> service.cancelDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_ON_DUTY));
    }

    @Test
    @DisplayName("이미 완료한 지정은 409 DUTY_ALREADY_COMPLETED로 취소할 수 없고 알림도 남는다")
    void cancelCompletedIsRejected() {
        givenAdjustable(1);
        givenTodayDuty(DutyStatus.COMPLETED);
        given(volunteerDutyRepository.cancel(50L)).willReturn(0);

        assertThatThrownBy(() -> service.cancelDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUTY_ALREADY_COMPLETED));
        verify(notificationService, never()).delete(anyLong(), any(), any());
    }

    @Test
    @DisplayName("완료하면 지정을 완료로 바꾸고 -1 기록을 남긴 뒤 봉사 횟수를 1 줄인다")
    void completeDecreasesCount() {
        givenAdjustable(2);
        givenTodayDuty(DutyStatus.ASSIGNED);
        given(volunteerDutyRepository.complete(50L, NOW)).willReturn(1);
        given(studentRepository.decreaseVolunteerCount(10L)).willReturn(1);

        service.completeDuty(MEMBER_ID, 200L);

        verify(volunteerAdjustmentRepository).insertIfAbsent(10L, -1, -1, null, null, NOW);
        verify(studentRepository).decreaseVolunteerCount(10L);
    }

    @Test
    @DisplayName("이미 완료했으면 409 DUTY_ALREADY_COMPLETED이고 다시 차감하지 않는다")
    void completeTwiceDoesNotDecreaseAgain() {
        givenAdjustable(1);
        givenTodayDuty(DutyStatus.COMPLETED);
        given(volunteerDutyRepository.complete(50L, NOW)).willReturn(0);

        assertThatThrownBy(() -> service.completeDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUTY_ALREADY_COMPLETED));
        verify(studentRepository, never()).decreaseVolunteerCount(anyLong());
    }

    @Test
    @DisplayName("그 사이 봉사 횟수가 0이 됐으면 409 VOLUNTEER_COUNT_ZERO다")
    void completeAtZeroIsRejected() {
        givenAdjustable(0);
        givenTodayDuty(DutyStatus.ASSIGNED);
        given(volunteerDutyRepository.complete(50L, NOW)).willReturn(1);
        given(studentRepository.decreaseVolunteerCount(10L)).willReturn(0);

        assertThatThrownBy(() -> service.completeDuty(MEMBER_ID, 200L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VOLUNTEER_COUNT_ZERO));
    }

    @Test
    @DisplayName("관리자가 아니면 403이고 학생을 조회하지 않는다")
    void nonAdminIsRejected() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(MEMBER_ID);

        assertThatThrownBy(() -> service.getVolunteers(MEMBER_ID, null, null, null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
        verify(studentRepository, never()).findAllByDatagsmStudentIdIsNotNull();
        assertThatThrownBy(() -> service.increase(MEMBER_ID, 200L, null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.decrease(MEMBER_ID, 200L, null)).isInstanceOf(CustomException.class);
        verify(volunteerAdjustmentRepository, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), any(), any(), any());
        assertThatThrownBy(() -> service.assignDuty(MEMBER_ID, 200L)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.cancelDuty(MEMBER_ID, 200L)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.completeDuty(MEMBER_ID, 200L)).isInstanceOf(CustomException.class);
        verify(volunteerDutyRepository, never()).assign(anyLong(), any(), any());
    }

    private void givenStudents(Student... students) {
        given(studentRepository.findAllByDatagsmStudentIdIsNotNull()).willReturn(List.of(students));
    }

    private static Student student(Long datagsmId, String name, int studentNumber, Integer room) {
        return Student.create(Member.create(datagsmId + 1000, name, MemberRole.STUDENT), datagsmId, name, 2, 1, 1,
                studentNumber, room);
    }

    /** DataGSM id 200, 내부 id 10, 봉사 횟수 {@code count}인 학생을 저장소가 돌려주게 한다. */
    private Student givenAdjustable(int count) {
        Student student = student(200L, "학생", 2105, 301);
        ReflectionTestUtils.setField(student, "id", 10L);
        ReflectionTestUtils.setField(student, "volunteerCount", count);
        given(studentRepository.findByDatagsmStudentId(200L)).willReturn(Optional.of(student));
        given(studentRepository.lockVolunteerCount(10L)).willReturn(Optional.of(count));
        given(studentRepository.findById(10L)).willReturn(Optional.of(student));
        return student;
    }

    private static VolunteerAdjustmentRepository.LastActivity lastActivity(Long studentId, Instant at) {
        return new VolunteerAdjustmentRepository.LastActivity() {
            @Override
            public Long getStudentId() {
                return studentId;
            }

            @Override
            public Instant getLastActivityAt() {
                return at;
            }
        };
    }

    /** 2026-10-01 12:00 KST이므로 오늘 운영일은 2026-10-01이다. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private VolunteerDuty givenTodayDuty(DutyStatus status) {
        VolunteerDuty duty = BeanUtils.instantiateClass(VolunteerDuty.class);
        ReflectionTestUtils.setField(duty, "id", 50L);
        ReflectionTestUtils.setField(duty, "status", status);
        given(volunteerDutyRepository.findByStudentIdAndOperatingDay(10L, TODAY)).willReturn(Optional.of(duty));
        return duty;
    }

    /** 학생 {@code studentId}에 {@code delta}로 남은 조정 기록. */
    private static VolunteerAdjustment adjustment(Long studentId, int delta) {
        return adjustment(studentId, delta, delta);
    }

    private static VolunteerAdjustment adjustment(Long studentId, int delta, int requestedDelta) {
        Student student = student(studentId, "학생", 2105, 301);
        ReflectionTestUtils.setField(student, "id", studentId);
        VolunteerAdjustment adjustment = BeanUtils.instantiateClass(VolunteerAdjustment.class);
        ReflectionTestUtils.setField(adjustment, "student", student);
        ReflectionTestUtils.setField(adjustment, "delta", (short) delta);
        ReflectionTestUtils.setField(adjustment, "requestedDelta", (short) requestedDelta);
        return adjustment;
    }
}
