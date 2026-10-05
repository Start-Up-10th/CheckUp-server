package com.checkup.checkup.domain.webhook.service;

import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import com.checkup.checkup.global.config.AdminProperties;
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
    private final AdminProperties adminProperties;

    /**
     * 받은 학생들을 반영한다. {@code syncedAt}보다 새로운 정보를 이미 반영한 학생은 건너뛴다.
     * 저장되지 않은 재학생은 로그인 계정 없이 새로 저장한다.
     *
     * @param students DataGSM에서 받은 학생 목록
     * @param syncedAt 이 정보의 기준 시각. 웹훅은 이벤트 시각, 수동 동기화는 목록을 받은 시각이다.
     * @return 반영하거나 새로 저장한 학생 수
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
            if (student == null) {
                if (create(changed, syncedAt)) {
                    synced++;
                }
                continue;
            }
            if (student.isStale(syncedAt)) {
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
     * 저장되지 않은 학생을 로그인 계정 없이 새로 저장한다. 졸업·자퇴생과 필요한 값이 빠진 학생은 저장하지 않는다.
     *
     * @return 저장했으면 {@code true}, 건너뛰었으면 {@code false}
     */
    private boolean create(StudentSyncData changed, Instant syncedAt) {
        if (GRADUATE.equals(changed.role()) || WITHDRAWN.equals(changed.role())) {
            return false;
        }
        if (!isComplete(changed)) {
            return false;
        }
        Student student = Student.createWithoutMember(
                changed.datagsmStudentId(),
                changed.name(), changed.grade(),
                changed.classNum(), changed.number(),
                changed.studentNumber(),
                changed.dormitoryRoom()
        );
        student.markSynced(syncedAt);
        studentRepository.save(student);
        return true;
    }

    /**
     * 학생 한 명의 변경을 반영한다. 관리자 권한은 요청마다 DB 역할로 확인하므로 기존 로그인 세션에도 바로 반영된다.
     *
     * 로그인 계정이 없는 학생은 회원 이름·권한 없이 학생 정보만 바꾼다.
     * 졸업·자퇴는 학년 등이 {@code null}로 오므로 학년·반·번호는 그대로 두고, 관리자 권한을 빼고 호실을 비운 뒤
     * {@link StudentLeftEvent}를 발행한다.
     * 재학생인데 필요한 값이 없으면 부분 데이터로 보고 건너뛴다.
     *
     * @return 반영했으면 {@code true}, 건너뛰었으면 {@code false}
     */
    private boolean sync(Student student, StudentSyncData changed) {
        Member member = student.getMember();

        if (GRADUATE.equals(changed.role()) || WITHDRAWN.equals(changed.role())) {
            if (member != null) {
                member.update(member.getName(), roleFor(member, MemberRole.STUDENT));
            }
            student.leaveDormitory();
            eventPublisher.publishEvent(new StudentLeftEvent(student.getId(), changed.role()));
            log.info("Student left: studentId={}, role={}", changed.datagsmStudentId(), changed.role());
            return true;
        }

        MemberRole role = toMemberRole(changed.role());
        if (!isComplete(changed)) {
            return false;
        }

        if (member != null) {
            member.update(changed.name(), roleFor(member, role));
        }
        student.update(
                changed.datagsmStudentId(),
                changed.name(),
                changed.grade(),
                changed.classNum(),
                changed.number(),
                changed.studentNumber(),
                changed.dormitoryRoom());
        return true;
    }

    /**
     * 재학생 정보에 이름·학년·반·번호·학번과 알 수 있는 role이 모두 있는지 확인한다. 없으면 부분 데이터로 보고 로그를 남긴다.
     */
    private static boolean isComplete(StudentSyncData changed) {
        if (changed.name() == null
                || changed.grade() == null
                || changed.classNum() == null
                || changed.number() == null
                || changed.studentNumber() == null
                || toMemberRole(changed.role()) == null
        ) {
            log.warn("Skipped incomplete student: studentId={}", changed.datagsmStudentId());
            return false;
        }
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

    private MemberRole roleFor(Member member, MemberRole role) {
        return adminProperties.isAllowed(member.getDatagsmId()) ? MemberRole.ADMIN : role;
    }
}
