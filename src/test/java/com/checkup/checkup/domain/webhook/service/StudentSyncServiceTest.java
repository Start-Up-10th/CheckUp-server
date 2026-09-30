package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import org.springframework.context.ApplicationEventPublisher;

/**
 * 받은 목록에 있는 학생만 조회·반영하고 목록에 없는 학생은 삭제·졸업 처리하지 않는지,
 * 실제로 반영한 학생 수만 돌려주는지 검증한다. 학생 한 명의 반영 규칙은 {@code WebhookServiceTest}에서 검증한다.
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
    @DisplayName("반영한 학생 수만 센다. 저장되지 않은 학생·이미 더 새로운 정보를 반영한 학생·부분 데이터는 빠진다")
    void onlySyncedStudentsAreCounted() {
        Student synced = student(1L, MemberRole.STUDENT, 301);
        Student stale = student(2L, MemberRole.STUDENT, 301);
        stale.markSynced(SYNCED_AT.plusSeconds(60));
        Student incomplete = student(3L, MemberRole.STUDENT, 301);
        given(studentRepository.findAllByDatagsmStudentIdIn(any())).willReturn(List.of(synced, stale, incomplete));

        int count = service.syncAll(List.of(
                enrolled(1L, 302),
                enrolled(2L, 302),
                new StudentSyncData(3L, "학생", null, 1, 5, 2105, 302, "GENERAL_STUDENT"),
                enrolled(99L, 302)), SYNCED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(synced.getDatagsmSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(stale.getDormitoryRoom()).isEqualTo(301);
        assertThat(incomplete.getDormitoryRoom()).isEqualTo(301);
    }

    private static Student student(Long datagsmStudentId, MemberRole role, Integer dormitoryRoom) {
        Member member = Member.create(datagsmStudentId + 1000, "학생", role);
        return Student.create(member, datagsmStudentId, 2, 1, 5, 2105, dormitoryRoom);
    }

    private static StudentSyncData enrolled(Long datagsmStudentId, Integer dormitoryRoom) {
        return new StudentSyncData(datagsmStudentId, "학생", 2, 1, 5, 2105, dormitoryRoom, "GENERAL_STUDENT");
    }
}
