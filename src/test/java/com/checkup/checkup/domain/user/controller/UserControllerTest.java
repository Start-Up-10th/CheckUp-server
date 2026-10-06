package com.checkup.checkup.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.user.dto.Response.UserVolunteerHistoryResponse;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.domain.user.serivce.UserSearchService;
import com.checkup.checkup.domain.user.serivce.UserVolunteerService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.GlobalExceptionHandler;
import com.checkup.checkup.global.ratelimit.RateLimiter;
import com.checkup.checkup.global.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 학생 정보·봉사 횟수·봉사 완료 내역 조회 API의 응답 필드와 401·400·403·404 응답을 검증한다.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private UserSearchService userSearchService;

    @MockitoBean
    private UserVolunteerService userVolunteerService;

    @Test
    @DisplayName("봉사 횟수를 studentId와 volunteerCount JSON으로 반환한다")
    void returnsVolunteerCountAsJson() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willReturn(new UserVolunteerResponse(STUDENT_ID, 3));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(100))
                .andExpect(jsonPath("$.volunteerCount").value(3));
    }

    @Test
    @DisplayName("미인증 요청은 서비스를 호출하지 않고 401을 반환한다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID))
                .andExpect(status().isUnauthorized());

        verify(userVolunteerService, never()).findVolunteer(MEMBER_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("학생 id가 숫자가 아니면 400을 반환한다")
    void nonNumericStudentIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc/volunteer").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("권한 오류는 FORBIDDEN 오류 응답으로 반환한다")
    void permissionErrorUsesErrorResponse() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("학생이 없으면 STUDENT_NOT_FOUND 오류 응답으로 반환한다")
    void missingStudentReturnsNotFound() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.STUDENT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("봉사 완료 내역을 studentId와 운영일·완료 시각 목록 JSON으로 반환한다")
    void returnsVolunteerHistoryAsJson() throws Exception {
        given(userVolunteerService.findVolunteerHistory(MEMBER_ID, STUDENT_ID))
                .willReturn(new UserVolunteerHistoryResponse(STUDENT_ID, List.of(
                        new UserVolunteerHistoryResponse.Item(
                                LocalDate.of(2026, 10, 3), Instant.parse("2026-10-03T09:00:00Z")))));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer/history", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(100))
                .andExpect(jsonPath("$.history[0].operatingDay").value("2026-10-03"))
                .andExpect(jsonPath("$.history[0].completedAt").value("2026-10-03T09:00:00Z"));
    }

    @Test
    @DisplayName("봉사 완료 내역도 미인증 요청은 401이다")
    void historyWithoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer/history", STUDENT_ID))
                .andExpect(status().isUnauthorized());

        verify(userVolunteerService, never()).findVolunteerHistory(MEMBER_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("봉사 완료 내역의 권한 오류는 FORBIDDEN 오류 응답으로 반환한다")
    void historyPermissionErrorUsesErrorResponse() throws Exception {
        given(userVolunteerService.findVolunteerHistory(MEMBER_ID, STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer/history", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
