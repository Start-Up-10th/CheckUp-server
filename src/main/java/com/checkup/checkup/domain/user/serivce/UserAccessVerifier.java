package com.checkup.checkup.domain.user.serivce;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DataGSM 학생 id 기준 조회 API의 접근 권한을 확인한다. 관리자이거나 본인 학생 id일 때만 허용한다.
 */
@Component
@RequiredArgsConstructor
public class UserAccessVerifier {

    private final StudentRepository studentRepository;

    /**
     * @param requester 세션의 로그인 회원
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 관리자도 본인도 아니면 {@link ErrorCode#FORBIDDEN}(403)
     */
    public void verify(Member requester, Long studentId) {
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
