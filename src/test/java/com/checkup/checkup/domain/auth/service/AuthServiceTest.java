package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DataGSM 사용자 정보로 서비스 역할을 판정하는 규칙(REQ-AUTH-003)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/callback";
    private static final String CODE = "code";
    private static final String STATE = "state";
    private static final String CODE_VERIFIER = "verifier";
    private static final String ACCESS_TOKEN = "test-access-token";

    @Mock
    private DataGsmOAuthClient dataGsmOAuthClient;

    @Mock
    private OAuthStateService oAuthStateService;

    @Mock
    private MemberService memberService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(dataGsmOAuthClient, oAuthStateService, REDIRECT_URI, memberService);
    }

    @Test
    @DisplayName("기숙사 자치위원 학생은 ADMIN이다")
    void dormitoryManagerStudentIsAdmin() {
        UserInfo userInfo = studentUser(StudentRole.DORMITORY_MANAGER);
        givenLoginReturns(userInfo);

        authService.completeLogin(CODE, STATE);

        verify(memberService).saveOrUpdate(userInfo, MemberRole.ADMIN);
    }

    @Test
    @DisplayName("학생회 학생은 관리자가 아니라 STUDENT다")
    void studentCouncilIsStudent() {
        UserInfo userInfo = studentUser(StudentRole.STUDENT_COUNCIL);
        givenLoginReturns(userInfo);

        authService.completeLogin(CODE, STATE);

        verify(memberService).saveOrUpdate(userInfo, MemberRole.STUDENT);
    }

    @Test
    @DisplayName("일반 학생은 STUDENT다")
    void generalStudentIsStudent() {
        UserInfo userInfo = studentUser(StudentRole.GENERAL_STUDENT);
        givenLoginReturns(userInfo);

        authService.completeLogin(CODE, STATE);

        verify(memberService).saveOrUpdate(userInfo, MemberRole.STUDENT);
    }

    @Test
    @DisplayName("최상위 role이 ADMIN이어도 일반 학생은 STUDENT다")
    void topLevelAdminRoleIsIgnored() {
        UserInfo userInfo = studentUser(StudentRole.GENERAL_STUDENT);
        userInfo.setRole(AccountRole.ADMIN);
        givenLoginReturns(userInfo);

        authService.completeLogin(CODE, STATE);

        verify(memberService).saveOrUpdate(userInfo, MemberRole.STUDENT);
    }

    @Test
    @DisplayName("기숙사부 교사는 ADMIN이다")
    void dormitoryTeacherIsAdmin() {
        UserInfo userInfo = teacherUser(TeacherDepartment.DORMITORY);
        givenLoginReturns(userInfo);

        authService.completeLogin(CODE, STATE);

        verify(memberService).saveOrUpdate(userInfo, MemberRole.ADMIN);
    }

    @Test
    @DisplayName("기숙사부가 아닌 교사는 403으로 거부한다")
    void otherTeacherIsForbidden() {
        givenLoginReturns(teacherUser(TeacherDepartment.GRADE));

        assertRejectedWith(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("teacher 정보가 없는 교사 계정은 403으로 거부한다")
    void teacherWithoutTeacherInfoIsForbidden() {
        UserInfo userInfo = teacherUser(TeacherDepartment.DORMITORY);
        userInfo.setTeacher(null);
        givenLoginReturns(userInfo);

        assertRejectedWith(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ACTIVE가 아닌 계정은 403으로 거부한다")
    void pendingAccountIsForbidden() {
        UserInfo userInfo = studentUser(StudentRole.DORMITORY_MANAGER);
        userInfo.setStatus(AccountStatus.PENDING);
        givenLoginReturns(userInfo);

        assertRejectedWith(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("student 정보가 없는 학생 계정은 403으로 거부한다")
    void studentWithoutStudentInfoIsForbidden() {
        UserInfo userInfo = studentUser(StudentRole.GENERAL_STUDENT);
        userInfo.setStudent(null);
        givenLoginReturns(userInfo);

        assertRejectedWith(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("student.role이 없는 학생 계정은 403으로 거부한다")
    void studentWithoutRoleIsForbidden() {
        givenLoginReturns(studentUser(null));

        assertRejectedWith(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("state가 없거나 만료되었으면 400으로 거부하고 토큰을 교환하지 않는다")
    void invalidStateIsBadRequest() {
        given(oAuthStateService.consume(STATE)).willReturn(Optional.empty());

        assertRejectedWith(HttpStatus.BAD_REQUEST);
        verify(dataGsmOAuthClient, never()).exchangeCodeForToken(anyString(), anyString(), anyString());
    }

    private void givenLoginReturns(UserInfo userInfo) {
        TokenResponse token = new TokenResponse();
        token.setAccessToken(ACCESS_TOKEN);
        given(oAuthStateService.consume(STATE)).willReturn(Optional.of(CODE_VERIFIER));
        given(dataGsmOAuthClient.exchangeCodeForToken(CODE, REDIRECT_URI, CODE_VERIFIER)).willReturn(token);
        given(dataGsmOAuthClient.getUserInfo(ACCESS_TOKEN)).willReturn(userInfo);
    }

    private void assertRejectedWith(HttpStatus status) {
        assertThatThrownBy(() -> authService.completeLogin(CODE, STATE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(status));
        verify(memberService, never()).saveOrUpdate(any(), any());
    }

    private static UserInfo studentUser(StudentRole role) {
        Student student = new Student();
        student.setId(1L);
        student.setName("학생");
        student.setRole(role);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(100L);
        userInfo.setRole(AccountRole.USER);
        userInfo.setStatus(AccountStatus.ACTIVE);
        userInfo.setObjectType(AccountObjectType.STUDENT);
        userInfo.setStudent(student);
        return userInfo;
    }

    private static UserInfo teacherUser(TeacherDepartment department) {
        Teacher teacher = new Teacher();
        teacher.setId(2L);
        teacher.setName("교사");
        teacher.setDepartment(department);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(200L);
        userInfo.setRole(AccountRole.USER);
        userInfo.setStatus(AccountStatus.ACTIVE);
        userInfo.setObjectType(AccountObjectType.TEACHER);
        userInfo.setTeacher(teacher);
        return userInfo;
    }
}
