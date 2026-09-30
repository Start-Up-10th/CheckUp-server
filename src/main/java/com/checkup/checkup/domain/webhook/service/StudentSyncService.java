package com.checkup.checkup.domain.webhook.service;

import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DataGSM에서 받은 학생 정보를 저장된 학생의 정보와 권한에 반영한다. 웹훅과 수동 동기화가 함께 쓴다.
 *
 * 받은 목록에 있는 학생만 반영한다. 목록에 없다는 이유로 저장된 학생을 삭제하거나 졸업 처리하지 않는다.
 * 로그에는 학생 ID·role만 남기고 이름 등 학생 정보는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentSyncService {

    private static final String GRADUATE = "GRADUATE";
    private static final String WITHDRAWN = "WITHDRAWN";

    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 받은 학생들을 반영한다. 로그인한 적 없는 학생과 {@code syncedAt}보다 새로운 정보를 이미 반영한 학생은 건너뛴다.
     *
     * @param students DataGSM에서 받은 학생 목록
     * @param syncedAt 이 정보의 기준 시각. 웹훅은 이벤트 시각, 수동 동기화는 목록을 받은 시각이다.
     * @return 반영한 학생 수
     */
    @Transactional
    public int syncAll(List<StudentSyncData> students, Instant syncedAt) {
        Map<Long, Student> saved = studentRepository.findAllByDatagsmStudentIdIn(
                        students.stream().map(StudentSyncData::datagsmStudentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getDatagsmStudentId, Function.identity()));

        int synced = 0;
        for (StudentSyncData changed : students) {
            Student student = saved.get(changed.datagsmStudentId());
            if (student == null || student.isStale(syncedAt)) {
                continue;
            }
            if (sync(student, changed)) {
                student.markSynced(syncedAt);
                synced++;
            }
        }
        return synced;
    }

    /**
     * 학생 한 명의 변경을 반영한다. 관리자 권한은 요청마다 DB 역할로 확인하므로 기존 로그인 세션에도 바로 반영된다.
     *
     * 졸업·자퇴는 학년 등이 {@code null}로 오므로 학년·반·번호는 그대로 두고, 관리자 권한을 빼고 호실을 비운 뒤
     * {@link StudentLeftEvent}를 발행한다.
     * 재학생인데 필요한 값이 없으면 부분 데이터로 보고 건너뛴다.
     *
     * @return 반영했으면 {@code true}, 건너뛰었으면 {@code false}
     */
    private boolean sync(Student student, StudentSyncData changed) {
        Member member = student.getMember();

        if (GRADUATE.equals(changed.role()) || WITHDRAWN.equals(changed.role())) {
            member.update(member.getName(), MemberRole.STUDENT);
            student.leaveDormitory();
            eventPublisher.publishEvent(new StudentLeftEvent(student.getId(), changed.role()));
            log.info("Student left: studentId={}, role={}", changed.datagsmStudentId(), changed.role());
            return true;
        }

        MemberRole role = toMemberRole(changed.role());
        if (role == null
                || changed.name() == null
                || changed.grade() == null
                || changed.classNum() == null
                || changed.number() == null
                || changed.studentNumber() == null) {
            log.warn("Skipped incomplete student: studentId={}", changed.datagsmStudentId());
            return false;
        }

        member.update(changed.name(), role);
        student.update(
                changed.datagsmStudentId(),
                changed.grade(),
                changed.classNum(),
                changed.number(),
                changed.studentNumber(),
                changed.dormitoryRoom());
        return true;
    }

    /**
     * DataGSM 학생 role을 서비스 권한으로 바꾼다. 기숙사 자치위원만 관리자다.
     *
     * @return 알 수 없는 role이면 {@code null}
     */
    private static MemberRole toMemberRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "DORMITORY_MANAGER" -> MemberRole.ADMIN;
            case "GENERAL_STUDENT", "STUDENT_COUNCIL" -> MemberRole.STUDENT;
            default -> null;
        };
    }
}
