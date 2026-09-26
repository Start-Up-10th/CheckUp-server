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

    public Member completeLogin(String code, String state) {
        String codeVerifier = oAuthStateService.consume(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "state가 유효하지 않거나 만료되었습니다."));

        TokenResponse token = dataGsmOAuthClient.exchangeCodeForToken(code, redirectUri, codeVerifier);
        UserInfo userInfo = dataGsmOAuthClient.getUserInfo(token.getAccessToken());
        MemberRole role = resolveRole(userInfo);
        return memberService.saveOrUpdate(userInfo, role);
    }

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
