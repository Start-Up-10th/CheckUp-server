package com.checkup.checkup.domain.volunteer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.GlobalExceptionHandler;
import com.checkup.checkup.global.security.SecurityConfig;
import java.time.Instant;
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
 * 봉사 관리 명단 API가 로그인한 회원 id로 서비스를 부르고 응답 필드·상태 코드를 지키는지 검증한다.
 */
@WebMvcTest(VolunteerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class VolunteerControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String BASE = "/api/v1/volunteer";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VolunteerService volunteerService;

    @Test
    @DisplayName("명단을 배열로 응답하고 최근 활동이 없으면 null이다")
    void listReturnsArray() throws Exception {
        given(volunteerService.getVolunteers(MEMBER_ID, null, null))
                .willReturn(List.of(new VolunteerResponse(200L, "학생", 2105, 301, 3, 2, null)));

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(200))
                .andExpect(jsonPath("$[0].name").value("학생"))
                .andExpect(jsonPath("$[0].studentNumber").value(2105))
                .andExpect(jsonPath("$[0].dormitoryRoom").value(301))
                .andExpect(jsonPath("$[0].dormitoryFloor").value(3))
                .andExpect(jsonPath("$[0].volunteerCount").value(2))
                .andExpect(jsonPath("$[0].lastActivityAt").isEmpty());
    }

    @Test
    @DisplayName("층과 검색어를 서비스로 넘긴다")
    void floorAndQueryArePassed() throws Exception {
        mockMvc.perform(get(BASE).param("floor", "4").param("q", "412").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk());

        verify(volunteerService).getVolunteers(MEMBER_ID, 4, "412");
    }

    @Test
    @DisplayName("증가·차감은 경로의 학생 id와 Idempotency-Key로 처리하고 바뀐 항목을 응답한다")
    void adjustReturnsUpdatedItem() throws Exception {
        VolunteerResponse updated = new VolunteerResponse(200L, "학생", 2105, 301, 3, 3, Instant.parse("2026-10-01T03:00:00Z"));
        given(volunteerService.increase(MEMBER_ID, 200L, "key-1")).willReturn(updated);
        given(volunteerService.decrease(MEMBER_ID, 200L, null)).willReturn(updated);

        mockMvc.perform(patch(BASE + "/200/count/increase").header("Idempotency-Key", "key-1").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volunteerCount").value(3))
                .andExpect(jsonPath("$.lastActivityAt").value("2026-10-01T03:00:00Z"));
        mockMvc.perform(patch(BASE + "/200/count/decrease").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk());

        verify(volunteerService).increase(MEMBER_ID, 200L, "key-1");
        verify(volunteerService).decrease(MEMBER_ID, 200L, null);
    }

    @Test
    @DisplayName("관리자가 아니면 403 ADMIN_ONLY로 응답한다")
    void nonAdminReturnsForbidden() throws Exception {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(volunteerService).getVolunteers(MEMBER_ID, null, null);

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());

        verify(volunteerService, never()).getVolunteers(any(), any(), any());
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
