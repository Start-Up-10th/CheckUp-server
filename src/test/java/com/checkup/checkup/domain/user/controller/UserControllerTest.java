package com.checkup.checkup.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.domain.user.serivce.UserSearchService;
import com.checkup.checkup.domain.user.serivce.UserVolunteerService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.GlobalExceptionHandler;
import com.checkup.checkup.global.security.SecurityConfig;
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

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSearchService userSearchService;

    @MockitoBean
    private UserVolunteerService userVolunteerService;

    @Test
    @DisplayName("봉사 횟수를 studentId와 volunteerCount JSON으로 반환한다")
    void 봉사횟수를_JSON으로_반환한다() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willReturn(new UserVolunteerResponse(STUDENT_ID, 3));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(100))
                .andExpect(jsonPath("$.volunteerCount").value(3));
    }

    @Test
    @DisplayName("미인증 요청은 서비스를 호출하지 않고 401을 반환한다")
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID))
                .andExpect(status().isUnauthorized());

        verify(userVolunteerService, never()).findVolunteer(MEMBER_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("학생 id가 숫자가 아니면 400을 반환한다")
    void 학생_id가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc/volunteer").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("권한 오류는 FORBIDDEN 오류 응답으로 반환한다")
    void 권한_오류를_오류_응답으로_반환한다() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("학생이 없으면 STUDENT_NOT_FOUND 오류 응답으로 반환한다")
    void 학생이_없으면_404이다() throws Exception {
        given(userVolunteerService.findVolunteer(MEMBER_ID, STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.STUDENT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/{studentId}/volunteer", STUDENT_ID).with(loginAs(MEMBER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
