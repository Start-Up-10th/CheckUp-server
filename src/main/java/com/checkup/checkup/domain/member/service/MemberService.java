package com.checkup.checkup.domain.member.service;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminRoleCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

/**
 * 회원·학생 정보의 저장과 조회를 담당한다.
 */
@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final AdminRoleCache adminRoleCache;

    /**
     * DataGSM 사용자 정보로 회원을 저장하거나 갱신한다. 학생이면 학생 정보도 함께 저장·갱신한다.
     *
     * 학생은 계정으로 먼저 찾고, 없으면 동기화로 미리 저장된 학생을 DataGSM 학생 id로 찾아 계정을 연결한다.
     * 둘 다 없을 때만 새로 저장하고 계정을 연결한다.
     *
     * @param userInfo DataGSM 사용자 정보
     * @param role     판정된 역할
     * @return 저장·갱신된 회원
     */
    @Transactional
    public Member saveOrUpdate(UserInfo userInfo, MemberRole role) {
        var dataGsmStudent = userInfo.getStudent();
        String name = dataGsmStudent != null
                ? dataGsmStudent.getName()
                : userInfo.getTeacher().getName();

        Member member = memberRepository.findByDatagsmId(userInfo.getId())
                .orElseGet(() -> memberRepository.save(Member.create(userInfo.getId(), name, role)));
        member.update(name, role);
        adminRoleCache.evictAfterCommit(member.getId());

        if (dataGsmStudent != null) {
            Student student = studentRepository.findByMember(member)
                    .orElseGet(() -> findOrInsert(dataGsmStudent, name));
            student.linkMember(member);
            student.update(
                    dataGsmStudent.getId(),
                    name,
                    dataGsmStudent.getGrade(),
                    dataGsmStudent.getClassNum(),
                    dataGsmStudent.getNumber(),
                    dataGsmStudent.getStudentNumber(),
                    dataGsmStudent.getDormitoryRoom());
        }

        return member;
    }

    /**
     * 동기화로 미리 저장된 학생을 DataGSM 학생 id로 찾고, 없으면 로그인 계정 없이 새로 저장한 뒤 다시 찾는다.
     * 같은 학생을 동기화가 동시에 저장해도 {@link StudentRepository#insertIfAbsent}가 건너뛰므로 unique 충돌이 나지 않는다(#152).
     */
    private Student findOrInsert(team.themoment.datagsm.sdk.oauth.model.Student dataGsmStudent, String name) {
        return studentRepository.findByDatagsmStudentId(dataGsmStudent.getId())
                .orElseGet(() -> {
                    studentRepository.insertIfAbsent(
                            dataGsmStudent.getId(),
                            name,
                            dataGsmStudent.getGrade(),
                            dataGsmStudent.getClassNum(),
                            dataGsmStudent.getNumber(),
                            dataGsmStudent.getStudentNumber(),
                            dataGsmStudent.getDormitoryRoom(),
                            null);
                    return studentRepository.findByDatagsmStudentId(dataGsmStudent.getId()).orElseThrow();
                });
    }

    /**
     * id로 회원을 조회한다.
     *
     * @throws CustomException 회원이 없으면 MEMBER_NOT_FOUND(401)
     */
    @Transactional(readOnly = true)
    public Member getById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
