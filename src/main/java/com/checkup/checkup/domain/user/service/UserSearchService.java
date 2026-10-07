package com.checkup.checkup.domain.user.service;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.response.UserSearchResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.DataGsmErrorCodes;
import com.checkup.checkup.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.exception.DataGsmException;
import team.themoment.datagsm.sdk.openapi.model.Student;

/**
 * DataGSM OpenAPI로 학생 정보를 조회한다. 관리자와 본인만 조회할 수 있다.
 * 권한은 캐시보다 먼저 검사하고, 조회 결과만 {@link UserSearchCache}에 짧게 보관한다.
 * DataGSM 호출이 실패하면 원인을 알 수 있게 예외 종류와 상태만 로그에 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchService {

    private static final int NOT_FOUND = 404;

    private final DataGsmOpenApiClient dataGsmOpenApiClient;
    private final MemberService memberService;
    private final UserAccessVerifier userAccessVerifier;
    private final UserSearchCache userSearchCache;

    /**
     * @param memberId  세션의 로그인 회원 id
     * @param studentId 조회할 DataGSM 학생 id
     * @throws CustomException 관리자도 본인도 아니면 {@link ErrorCode#FORBIDDEN}(403),
     *                         DataGSM에 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         DataGSM에 닿지 못하거나 DataGSM 서버 오류면 {@link ErrorCode#DATAGSM_UNAVAILABLE}(503),
     *                         그 밖의 DataGSM 호출 실패는 {@link ErrorCode#DATAGSM_ERROR}(502)
     */
    public UserSearchResponse findUser(Long memberId, Long studentId) {
        Member requester = memberService.getById(memberId);
        userAccessVerifier.verify(requester, studentId);

        Optional<UserSearchResponse> cached = userSearchCache.get(studentId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Student student;
        try {
            student = dataGsmOpenApiClient.students().getStudent(studentId);
        } catch (DataGsmException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
            }
            log.warn("DataGSM student lookup failed: {}", DataGsmErrorCodes.describe(e));
            throw new CustomException(DataGsmErrorCodes.of(e));
        }
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        UserSearchResponse response = UserSearchResponse.from(student);
        userSearchCache.put(studentId, response);
        return response;
    }

}
