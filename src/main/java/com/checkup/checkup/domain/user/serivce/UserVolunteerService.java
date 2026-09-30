package com.checkup.checkup.domain.user.serivce;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생의 누적 봉사 횟수를 조회한다. 관리자와 본인만 조회할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class UserVolunteerService {

    private final MemberService memberService;
    private final StudentRepository studentRepository;

    /**
     * @param memberId  세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 권한이 없으면 {@link ErrorCode#FORBIDDEN}, 저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public UserVolunteerResponse findVolunteer(Long memberId, Long studentId) {
        Member requester = memberService.getById(memberId);
        verifyAccess(requester, studentId);

        Student student = studentRepository.findByDatagsmStudentId(studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return new UserVolunteerResponse(studentId, student.getVolunteerCount());
    }

    private void verifyAccess(Member requester, Long studentId) {
        if (requester.getRole() == MemberRole.ADMIN) {
            return;
        }
        boolean self = studentRepository.findByMember(requester)
                .map(s -> studentId.equals(s.getDatagsmStudentId()))
                .orElse(false);
        if (!self) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
