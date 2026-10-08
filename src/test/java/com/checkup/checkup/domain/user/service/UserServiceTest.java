package com.checkup.checkup.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.response.UserSearchResponse;
import com.checkup.checkup.domain.user.dto.response.Sex;
import com.checkup.checkup.domain.user.dto.response.StudentRole;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.client.StudentApi;
import team.themoment.datagsm.sdk.openapi.exception.DataGsmException;
import team.themoment.datagsm.sdk.openapi.exception.ServerErrorException;

import com.checkup.checkup.global.security.AdminRoleCache;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.support.MutableClock;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 학생 조회의 접근 권한(관리자·본인)과 DataGSM 응답 변환을 검증한다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class UserServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 100L;

    @Mock
    private DataGsmOpenApiClient dataGsmOpenApiClient;

    @Mock
    private StudentApi studentApi;

    @Mock
    private MemberService memberService;

    @Mock
    private StudentRepository studentRepository;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));

    private UserSearchService userSearchService;

    @BeforeEach
    void setUp() {
        userSearchService = new UserSearchService(
                dataGsmOpenApiClient,
                new UserAccessVerifier(new AdminVerifier(memberService, new AdminRoleCache(clock)), studentRepository),
                new UserSearchCache(clock));
    }

    @Test
    @DisplayName("관리자는 다른 학생을 조회할 수 있고 응답이 변환된다")
    void adminCanFindAnyStudent() {
        givenMember(MemberRole.ADMIN);
        givenDataGsmStudent(sdkStudent());

        UserSearchResponse response = userSearchService.findUser(MEMBER_ID, STUDENT_ID);

        assertThat(response.id()).isEqualTo(STUDENT_ID);
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.email()).isEqualTo("s26001@gsm.hs.kr");
        assertThat(response.sex()).isEqualTo(Sex.MAN);
        assertThat(response.studentRole()).isEqualTo(StudentRole.GENERAL_STUDENT);
        assertThat(response.grade()).isEqualTo(2);
        assertThat(response.classNumber()).isEqualTo(3);
        assertThat(response.number()).isEqualTo(4);
        assertThat(response.dormitoryRoom()).isEqualTo(301);
    }

    @Test
    @DisplayName("학생은 본인 정보를 조회할 수 있다")
    void studentCanFindSelf() {
        givenMember(MemberRole.STUDENT);
        givenOwnStudent(MEMBER_ID, STUDENT_ID);
        givenDataGsmStudent(sdkStudent());

        UserSearchResponse response = userSearchService.findUser(MEMBER_ID, STUDENT_ID);

        assertThat(response.id()).isEqualTo(STUDENT_ID);
    }

    @Test
    @DisplayName("학생이 다른 학생을 조회하면 FORBIDDEN이고 DataGSM을 호출하지 않는다")
    void studentCannotFindOthers() {
        givenMember(MemberRole.STUDENT);
        givenOwnStudent(MEMBER_ID, 999L);

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(dataGsmOpenApiClient, never()).students();
    }

    @Test
    @DisplayName("학생 정보가 없는 STUDENT 회원은 FORBIDDEN이다")
    void studentWithoutStudentRecordIsForbidden() {
        givenMember(MemberRole.STUDENT);
        given(studentRepository.findDatagsmStudentIdByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("DataGSM에 학생이 없으면 STUDENT_NOT_FOUND다")
    void notFoundWhenStudentMissing() {
        givenMember(MemberRole.ADMIN);
        givenDataGsmStudent(null);

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
    }

    @Test
    @DisplayName("그 밖의 DataGSM 호출 실패는 DATAGSM_ERROR다")
    void badGatewayWhenDataGsmFails() {
        givenMember(MemberRole.ADMIN);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(anyLong())).willThrow(new DataGsmException("fail"));

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_ERROR));
    }

    @Test
    @DisplayName("DataGSM에 닿지 못하거나 시간이 초과되면 DATAGSM_UNAVAILABLE이다")
    void unavailableWhenDataGsmTimesOut() {
        givenMember(MemberRole.ADMIN);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(anyLong()))
                .willThrow(new DataGsmException("fail", new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_UNAVAILABLE));
    }

    @Test
    @DisplayName("DataGSM 서버 오류면 DATAGSM_UNAVAILABLE이다")
    void unavailableWhenDataGsmServerFails() {
        givenMember(MemberRole.ADMIN);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(anyLong())).willThrow(new ServerErrorException("fail"));

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_UNAVAILABLE));
    }

    @Test
    @DisplayName("DataGSM이 404를 주면 STUDENT_NOT_FOUND이고 경고 로그를 남기지 않는다")
    void notFoundWhenDataGsmReturns404(CapturedOutput output) {
        givenMember(MemberRole.ADMIN);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(anyLong())).willThrow(new DataGsmException("not found", 404));

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        assertThat(output.getOut()).doesNotContain("DataGSM student lookup failed");
    }

    @Test
    @DisplayName("DataGSM 호출 실패는 예외 종류와 상태만 로그에 남기고 SDK 메시지는 남기지 않는다")
    void failureIsLoggedWithoutUpstreamMessage(CapturedOutput output) {
        givenMember(MemberRole.ADMIN);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(anyLong())).willThrow(new DataGsmException("upstream-body", 418));

        assertThatThrownBy(() -> userSearchService.findUser(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_ERROR));
        assertThat(output.getOut())
                .contains("DataGSM student lookup failed: type=DataGsmException, status=418, cause=none")
                .doesNotContain("upstream-body");
    }

    @Test
    @DisplayName("TTL 안의 반복 조회는 DataGSM을 한 번만 호출하고 만료되면 다시 호출한다")
    void repeatedLookupWithinTtlCallsDataGsmOnce() {
        givenMember(MemberRole.ADMIN);
        givenDataGsmStudent(sdkStudent());

        userSearchService.findUser(MEMBER_ID, STUDENT_ID);
        clock.advance(Duration.ofSeconds(59));
        userSearchService.findUser(MEMBER_ID, STUDENT_ID);
        verify(studentApi, times(1)).getStudent(STUDENT_ID);

        clock.advance(Duration.ofSeconds(2));
        userSearchService.findUser(MEMBER_ID, STUDENT_ID);
        verify(studentApi, times(2)).getStudent(STUDENT_ID);
    }

    @Test
    @DisplayName("캐시에 있어도 권한이 없으면 FORBIDDEN이다")
    void cachedResultIsNotServedWithoutAccess() {
        Member admin = Member.create(10L, "관리자", MemberRole.ADMIN);
        Member student = Member.create(11L, "학생", MemberRole.STUDENT);
        given(memberService.getById(1L)).willReturn(admin);
        given(memberService.getById(2L)).willReturn(student);
        givenOwnStudent(2L, 999L);
        givenDataGsmStudent(sdkStudent());
        userSearchService.findUser(1L, STUDENT_ID);

        assertThatThrownBy(() -> userSearchService.findUser(2L, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private Member givenMember(MemberRole role) {
        Member member = Member.create(10L, "홍길동", role);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        return member;
    }

    private void givenOwnStudent(Long memberId, Long datagsmStudentId) {
        given(studentRepository.findDatagsmStudentIdByMemberId(memberId)).willReturn(Optional.of(datagsmStudentId));
    }

    private void givenDataGsmStudent(team.themoment.datagsm.sdk.openapi.model.Student student) {
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudent(STUDENT_ID)).willReturn(student);
    }

    private team.themoment.datagsm.sdk.openapi.model.Student sdkStudent() {
        var student = new team.themoment.datagsm.sdk.openapi.model.Student();
        student.setId(STUDENT_ID);
        student.setName("홍길동");
        student.setEmail("s26001@gsm.hs.kr");
        student.setSex(team.themoment.datagsm.sdk.openapi.model.Sex.MAN);
        student.setRole(team.themoment.datagsm.sdk.openapi.model.StudentRole.GENERAL_STUDENT);
        student.setGrade(2);
        student.setClassNum(3);
        student.setNumber(4);
        student.setDormitoryRoom(301);
        return student;
    }
}
