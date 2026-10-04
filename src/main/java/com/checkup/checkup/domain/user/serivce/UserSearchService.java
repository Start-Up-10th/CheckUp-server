package com.checkup.checkup.domain.user.serivce;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.Response.UserSearchResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.exception.DataGsmException;
import team.themoment.datagsm.sdk.openapi.model.Student;

/**
 * DataGSM OpenAPI로 학생 정보를 조회한다. 관리자와 본인만 조회할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class UserSearchService {

    private final DataGsmOpenApiClient dataGsmOpenApiClient;
    private final MemberService memberService;
    private final StudentRepository studentRepository;

    /**
     * @param memberId  세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 관리자도 본인도 아니면 {@link ErrorCode#FORBIDDEN}(403),
     *                         DataGSM에 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         DataGSM 호출이 실패하면 {@link ErrorCode#DATAGSM_ERROR}(502)
     */
    public UserSearchResponse findUser(Long memberId, Long studentId) {
        Member requester = memberService.getById(memberId);
        verifyAccess(requester, studentId);

        Student student;
        try {
            student = dataGsmOpenApiClient.students().getStudent(studentId);
        } catch (DataGsmException e) {
            throw new CustomException(ErrorCode.DATAGSM_ERROR);
        }
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return UserSearchResponse.from(student);
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
