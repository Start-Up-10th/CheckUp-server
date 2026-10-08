package com.checkup.checkup.domain.user.service;

import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DataGSM 학생 id 기준 조회 API의 접근 권한을 확인한다. 관리자이거나 본인 학생 id일 때만 허용한다.
 * 역할은 {@link AdminVerifier}가 짧게 기억한 값을 쓰고, 본인 확인에는 DataGSM 학생 id 한 값만 읽는다(#232).
 */
@Component
@RequiredArgsConstructor
public class UserAccessVerifier {

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;

    /**
     * @param memberId 세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 회원이 없으면 {@link ErrorCode#MEMBER_NOT_FOUND}(401),
     *                         관리자도 본인도 아니면 {@link ErrorCode#FORBIDDEN}(403)
     */
    public void verify(Long memberId, Long studentId) {
        if (adminVerifier.isAdmin(memberId)) {
            return;
        }
        boolean self = studentRepository.findDatagsmStudentIdByMemberId(memberId)
                .map(studentId::equals)
                .orElse(false);
        if (!self) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
