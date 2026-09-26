package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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

    public AuthService(
            DataGsmOAuthClient dataGsmOAuthClient,
            OAuthStateService oAuthStateService,
            @Value("${datagsm.redirect-uri}") String redirectUri, MemberService memberService
    ) {
        this.dataGsmOAuthClient = dataGsmOAuthClient;
        this.oAuthStateService = oAuthStateService;
        this.redirectUri = redirectUri;
        this.memberService = memberService;
    }

    /**
     * state와 PKCE code verifier를 만들어 Redis에 저장하고 DataGSM 인가 URL을 반환한다.
     *
     * @return DataGSM 인가 URL
     */
    public String createLoginUrl() {
        String state = UUID.randomUUID().toString();
        AuthorizationUrlBuilder builder = dataGsmOAuthClient
                .createAuthorizationUrl(redirectUri)
                .state(state)
                .enablePkce();
        String codeVerifier = builder.getCodeVerifier();
        oAuthStateService.save(state, codeVerifier);
        return builder.build();
    }

    /**
     * state를 검증하고 code를 토큰으로 교환한 뒤 사용자 정보로 회원을 저장·갱신한다.
     *
     * @param code  DataGSM 인가 코드
     * @param state 로그인 요청 때 발급한 state
     * @return 저장·갱신된 회원
     * @throws ResponseStatusException state가 없거나 만료되면 400, 이용 권한이 없는 계정이면 403
     */
    public Member completeLogin(String code, String state) {
        String codeVerifier = oAuthStateService.consume(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "state가 유효하지 않거나 만료되었습니다."));

        TokenResponse token = dataGsmOAuthClient.exchangeCodeForToken(code, redirectUri, codeVerifier);
        UserInfo userInfo = dataGsmOAuthClient.getUserInfo(token.getAccessToken());
        MemberRole role = resolveRole(userInfo);
        return memberService.saveOrUpdate(userInfo, role);
    }

    /**
     * 기숙사 자치위원 학생과 기숙사부 교사는 ADMIN, 그 외 활성 학생은 STUDENT로 판정한다.
     * 비활성 계정이나 그 밖의 계정은 403으로 거부한다.
     */
    private MemberRole resolveRole(UserInfo userInfo) {
        Student student = userInfo.getStudent();
        Teacher teacher = userInfo.getTeacher();

        if (userInfo.getStatus() != AccountStatus.ACTIVE) throw new  ResponseStatusException(HttpStatus.FORBIDDEN, "올바르지 않은 계정 상태입니다.");
        if (userInfo.getObjectType() == AccountObjectType.STUDENT
                && (student == null || student.getRole() == null)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생 정보가 없습니다.");
        if (userInfo.getObjectType() == AccountObjectType.STUDENT
                && student.getRole() == StudentRole.DORMITORY_MANAGER) return MemberRole.ADMIN;
        if (userInfo.getObjectType() == AccountObjectType.STUDENT) return MemberRole.STUDENT;
        if (userInfo.getObjectType() == AccountObjectType.TEACHER
                && teacher != null
                && teacher.getDepartment() == TeacherDepartment.DORMITORY) return MemberRole.ADMIN;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용 권한이 없는 계정입니다.");
    }
}
