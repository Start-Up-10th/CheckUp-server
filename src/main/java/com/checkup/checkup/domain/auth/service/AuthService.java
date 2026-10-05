package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.config.AdminProperties;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.*;

import java.util.UUID;

/**
 * DataGSM OAuth 인가 흐름(인가 URL 생성, 토큰 교환, 역할 판정)을 담당한다.
 */
@Service
public class AuthService {

    private final DataGsmOAuthClient dataGsmOAuthClient;
    private final OAuthStateService oAuthStateService;
    private final String redirectUri;
    private final MemberService memberService;
    private final AdminProperties adminProperties;

    public AuthService(
            DataGsmOAuthClient dataGsmOAuthClient,
            OAuthStateService oAuthStateService,
            @Value("${datagsm.redirect-uri}") String redirectUri, MemberService memberService,
            AdminProperties adminProperties
    ) {
        this.dataGsmOAuthClient = dataGsmOAuthClient;
        this.oAuthStateService = oAuthStateService;
        this.redirectUri = redirectUri;
        this.memberService = memberService;
        this.adminProperties = adminProperties;
    }

    /**
     * state와 PKCE code verifier를 만들어 로그인 후 돌아갈 경로와 함께 Redis에 저장하고 DataGSM 인가 URL을 반환한다.
     *
     * @param redirectPath 로그인 후 돌아갈 웹 경로. 안전하지 않거나 없으면 로그인 완료 화면으로 바뀐다.
     * @return DataGSM 인가 URL
     */
    public String createLoginUrl(String redirectPath) {
        String state = UUID.randomUUID().toString();
        AuthorizationUrlBuilder builder = dataGsmOAuthClient
                .createAuthorizationUrl(redirectUri)
                .state(state)
                .enablePkce();
        String codeVerifier = builder.getCodeVerifier();
        oAuthStateService.save(state, codeVerifier, LoginRedirectPath.sanitize(redirectPath));
        return builder.build();
    }

    /**
     * state를 검증하고 code를 토큰으로 교환한 뒤 사용자 정보로 회원을 저장·갱신한다.
     * 거절 규칙을 먼저 적용한 뒤, 관리자 허용 목록에 있는 계정은 ADMIN으로 정한다.
     *
     * @param code  DataGSM 인가 코드
     * @param state 로그인 요청 때 발급한 state
     * @return 저장·갱신된 회원과 로그인 시작 때 정한 돌아갈 경로
     * @throws CustomException state가 없거나 만료되면 INVALID_OAUTH_STATE, 이용 권한이 없는 계정이면 403 계열 코드
     */
    public LoginResult completeLogin(String code, String state) {
        OAuthState saved = oAuthStateService.consume(state)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_OAUTH_STATE));

        TokenResponse token = dataGsmOAuthClient.exchangeCodeForToken(code, redirectUri, saved.codeVerifier());
        UserInfo userInfo = dataGsmOAuthClient.getUserInfo(token.getAccessToken());
        MemberRole role = resolveRole(userInfo);
        if (adminProperties.isAllowed(userInfo.getId())) {
            role = MemberRole.ADMIN;
        }
        return new LoginResult(memberService.saveOrUpdate(userInfo, role), saved.redirectPath());
    }

    /**
     * 기숙사 자치위원 학생과 기숙사부 교사는 ADMIN, 그 외 활성 학생은 STUDENT로 판정한다.
     * 비활성 계정이나 그 밖의 계정은 403으로 거부한다.
     * 이름·학년·반·번호·학번이 하나라도 없는 학생도 403으로 거부한다. 졸업·자퇴하면 이 값이 비어 올 수 있고,
     * 값 없이는 학생을 저장할 수 없어 회원도 만들지 않는다.
     */
    private MemberRole resolveRole(UserInfo userInfo) {
        Student student = userInfo.getStudent();
        Teacher teacher = userInfo.getTeacher();

        if (userInfo.getStatus() != AccountStatus.ACTIVE) throw new CustomException(ErrorCode.INACTIVE_ACCOUNT);
        if (userInfo.getObjectType() == AccountObjectType.STUDENT
                && (student == null || student.getRole() == null)) throw new CustomException(ErrorCode.MISSING_STUDENT_INFO);
        if (userInfo.getObjectType() == AccountObjectType.STUDENT
                && !hasProfile(student)) throw new CustomException(ErrorCode.MISSING_STUDENT_INFO);
        if (userInfo.getObjectType() == AccountObjectType.STUDENT
                && student.getRole() == StudentRole.DORMITORY_MANAGER) return MemberRole.ADMIN;
        if (userInfo.getObjectType() == AccountObjectType.STUDENT) return MemberRole.STUDENT;
        if (userInfo.getObjectType() == AccountObjectType.TEACHER
                && teacher != null
                && teacher.getDepartment() == TeacherDepartment.DORMITORY) return MemberRole.ADMIN;
        throw new CustomException(ErrorCode.UNSUPPORTED_ACCOUNT);
    }

    /**
     * 학생을 저장하는 데 필요한 이름·학년·반·번호·학번이 모두 있는지 확인한다.
     */
    private static boolean hasProfile(Student student) {
        return student.getName() != null
                && student.getGrade() != null
                && student.getClassNum() != null
                && student.getNumber() != null
                && student.getStudentNumber() != null;
    }
}
