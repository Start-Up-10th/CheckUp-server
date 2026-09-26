package com.checkup.checkup.domain.member.service;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

/**
 * 회원·학생 정보의 저장과 조회를 담당한다.
 */
@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;

    /**
     * DataGSM 사용자 정보로 회원을 저장하거나 갱신한다. 학생이면 학생 정보도 함께 저장·갱신한다.
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

        if (dataGsmStudent != null) {
            studentRepository.findByMember(member).ifPresentOrElse(
                    student -> student.update(
                            dataGsmStudent.getId(),
                            dataGsmStudent.getGrade(),
                            dataGsmStudent.getClassNum(),
                            dataGsmStudent.getNumber(),
                            dataGsmStudent.getStudentNumber(),
                            dataGsmStudent.getDormitoryRoom()),
                    () -> studentRepository.save(Student.create(
                            member,
                            dataGsmStudent.getId(),
                            dataGsmStudent.getGrade(),
                            dataGsmStudent.getClassNum(),
                            dataGsmStudent.getNumber(),
                            dataGsmStudent.getStudentNumber(),
                            dataGsmStudent.getDormitoryRoom()))
            );
        }

        return member;
    }

    /**
     * id로 회원을 조회한다.
     *
     * @throws ResponseStatusException 회원이 없으면 401
     */
    @Transactional(readOnly = true)
    public Member getById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));
    }
}
