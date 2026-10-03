package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.auth.dto.response.CurrentStudentResponse;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션의 현재 회원 정보를 만든다({@code GET /api/v1/auth/me}).
 *
 * 학생 정보는 세션의 회원 id로만 찾는다. 웹은 이 응답으로 본인 학번·호실과 DataGSM 학생 id를 알고,
 * 다른 학생의 정보는 요청할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class CurrentMemberService {

    private final MemberService memberService;
    private final StudentRepository studentRepository;

    /**
     * @param memberId 세션의 회원 id
     * @return 이름·역할·필수 동의 여부·학생 정보(학생이 아니면 null)
     * @throws CustomException 회원이 없으면 MEMBER_NOT_FOUND(401)
     */
    @Transactional(readOnly = true)
    public OAuthLoginResponse getCurrentMember(Long memberId) {
        Member member = memberService.getById(memberId);
        Optional<Student> student = studentRepository.findByMemberId(memberId);
        return OAuthLoginResponse.from(
                member,
                student.map(Student::hasRequiredConsent).orElse(false),
                student.map(CurrentStudentResponse::from).orElse(null));
    }
}
