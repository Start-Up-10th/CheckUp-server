package com.checkup.checkup.domain.user.serivce;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerHistoryResponse;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.repository.VolunteerDutyRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생의 남은 봉사 횟수와 봉사 완료 내역을 조회한다. 관리자와 본인만 조회할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class UserVolunteerService {

    private final MemberService memberService;
    private final StudentRepository studentRepository;
    private final UserAccessVerifier userAccessVerifier;
    private final VolunteerDutyRepository volunteerDutyRepository;

    /**
     * @param memberId  세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 권한이 없으면 {@link ErrorCode#FORBIDDEN}, 저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public UserVolunteerResponse findVolunteer(Long memberId, Long studentId) {
        Member requester = memberService.getById(memberId);
        userAccessVerifier.verify(requester, studentId);

        Student student = studentRepository.findByDatagsmStudentId(studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return new UserVolunteerResponse(studentId, student.getVolunteerCount());
    }

    /**
     * 학생의 봉사 완료 내역을 최신 운영일부터 조회한다(REQ-COM-003). 지정만 되고 완료하지 않은 봉사는 넣지 않는다.
     *
     * @param memberId  세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 권한이 없으면 {@link ErrorCode#FORBIDDEN}, 저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public UserVolunteerHistoryResponse findVolunteerHistory(Long memberId, Long studentId) {
        Member requester = memberService.getById(memberId);
        userAccessVerifier.verify(requester, studentId);

        Student student = studentRepository.findByDatagsmStudentId(studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return new UserVolunteerHistoryResponse(studentId, volunteerDutyRepository
                .findAllByStudentIdAndStatusOrderByOperatingDayDescIdDesc(student.getId(), DutyStatus.COMPLETED)
                .stream()
                .map(UserVolunteerHistoryResponse.Item::from)
                .toList());
    }

}
