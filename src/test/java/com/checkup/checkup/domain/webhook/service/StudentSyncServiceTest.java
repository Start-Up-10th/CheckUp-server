package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 받은 목록에 있는 학생만 조회·반영하고 목록에 없는 학생은 삭제·졸업 처리하지 않는지,
 * 저장되지 않은 재학생은 계정 없이 새로 저장하는지, 실제로 반영한 학생 수만 돌려주는지 검증한다. 학생 한 명의 반영 규칙은 {@code WebhookServiceTest}에서 검증한다.
 */
class StudentSyncServiceTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-09-30T00:00:00Z");

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final StudentSyncService service = new StudentSyncService(studentRepository, eventPublisher);

    @Test
    @DisplayName("목록에 없는 저장된 학생은 조회하지도 바꾸지도 않는다")
    void studentMissingFromListIsKept() {
        Student listed = student(42L, MemberRole.STUDENT, 301);
        Student missing = student(43L, MemberRole.ADMIN, 401);
        given(studentRepository.findAllByDatagsmStudentIdIn(List.of(42L))).willReturn(List.of(listed));

        service.syncAll(List.of(enrolled(42L, 302)), SYNCED_AT);

        verify(studentRepository).findAllByDatagsmStudentIdIn(List.of(42L));
        verifyNoMoreInteractions(studentRepository);
        assertThat(missing.getMember().getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(missing.getDormitoryRoom()).isEqualTo(401);
        assertThat(missing.getDatagsmSyncedAt()).isNull();
        assertThat(listed.getDormitoryRoom()).isEqualTo(302);
    }

    @Test
    @DisplayName("반영한 학생 수만 센다. 이미 더 새로운 정보를 반영한 학생·부분 데이터는 빠진다")
    void onlySyncedStudentsAreCounted() {
        Student synced = student(1L, MemberRole.STUDENT, 301);
        Student stale = student(2L, MemberRole.STUDENT, 301);
        stale.markSynced(SYNCED_AT.plusSeconds(60));
        Student incomplete = student(3L, MemberRole.STUDENT, 301);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(synced, stale, incomplete));

        int count = service.syncAll(List.of(
                enrolled(1L, 302),
                enrolled(2L, 302),
                new StudentSyncData(3L, "학생", null, 1, 5, 2105, 302, "GENERAL_STUDENT")), SYNCED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(synced.getDatagsmSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(stale.getDormitoryRoom()).isEqualTo(301);
        assertThat(incomplete.getDormitoryRoom()).isEqualTo(301);
    }

    @Test
    @DisplayName("저장되지 않은 재학생은 계정 없이 새로 저장하고 반영한 수에 센다")
    void unsavedEnrolledStudentIsCreatedWithoutMember() {
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of());

        int count = service.syncAll(List.of(
                new StudentSyncData(99L, "새학생", 1, 2, 3, 1203, 405, "GENERAL_STUDENT")), SYNCED_AT);

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        Student created = saved.getValue();
        assertThat(count).isEqualTo(1);
        assertThat(created.getMember()).isNull();
        assertThat(created.getDatagsmStudentId()).isEqualTo(99L);
        assertThat(created.getName()).isEqualTo("새학생");
        assertThat(created.getStudentNumber()).isEqualTo(1203);
        assertThat(created.getDormitoryRoom()).isEqualTo(405);
        assertThat(created.getDatagsmSyncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    @DisplayName("저장되지 않은 졸업·자퇴생과 부분 데이터·알 수 없는 role 학생은 새로 저장하지 않는다")
    void unsavedLeftOrIncompleteStudentIsNotCreated() {
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of());

        int count = service.syncAll(List.of(
                new StudentSyncData(90L, "졸업생", null, null, null, null, null, "GRADUATE"),
                new StudentSyncData(91L, "자퇴생", null, null, null, null, null, "WITHDRAWN"),
                new StudentSyncData(92L, "학생", null, 1, 5, 2105, 302, "GENERAL_STUDENT"),
                new StudentSyncData(93L, "학생", 2, 1, 5, 2105, 302, "UNKNOWN")), SYNCED_AT);

        assertThat(count).isZero();
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("계정이 없는 저장된 학생도 이름과 호실을 갱신한다")
    void studentWithoutMemberIsUpdated() {
        Student student = Student.createWithoutMember(50L, "옛이름", 2, 1, 5, 2105, 301);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(student));

        int count = service.syncAll(List.of(
                new StudentSyncData(50L, "새이름", 2, 1, 5, 2105, 302, "DORMITORY_MANAGER")), SYNCED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(student.getMember()).isNull();
        assertThat(student.getName()).isEqualTo("새이름");
        assertThat(student.getDormitoryRoom()).isEqualTo(302);
    }

    @Test
    @DisplayName("계정이 없는 저장된 학생이 졸업하면 호실만 비운다")
    void studentWithoutMemberLeavesDormitory() {
        Student student = Student.createWithoutMember(51L, "졸업생", 3, 1, 5, 3105, 301);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(student));

        int count = service.syncAll(List.of(
                new StudentSyncData(51L, "졸업생", null, null, null, null, null, "GRADUATE")), SYNCED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(student.getDormitoryRoom()).isNull();
        assertThat(student.getStudentNumber()).isEqualTo(3105);
    }

    private static Student student(Long datagsmStudentId, MemberRole role, Integer dormitoryRoom) {
        Member member = Member.create(datagsmStudentId + 1000, "학생", role);
        return Student.create(member, datagsmStudentId, "학생", 2, 1, 5, 2105, dormitoryRoom);
    }

    private static StudentSyncData enrolled(Long datagsmStudentId, Integer dormitoryRoom) {
        return new StudentSyncData(datagsmStudentId, "학생", 2, 1, 5, 2105, dormitoryRoom, "GENERAL_STUDENT");
    }
}
