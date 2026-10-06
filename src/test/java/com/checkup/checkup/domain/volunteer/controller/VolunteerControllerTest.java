package com.checkup.checkup.domain.volunteer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.volunteer.dto.response.VolunteerAdjustmentResponse;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustmentKind;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.GlobalExceptionHandler;
import com.checkup.checkup.global.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
        given(volunteerService.getVolunteers(MEMBER_ID, null, null, null, null))
                .willReturn(List.of(new VolunteerResponse(200L, "학생", 2105, 301, 2, null, null)));

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(200))
                .andExpect(jsonPath("$[0].name").value("학생"))
                .andExpect(jsonPath("$[0].studentNumber").value(2105))
                .andExpect(jsonPath("$[0].dormitoryRoom").value(301))
                .andExpect(jsonPath("$[0].dormitoryFloor").doesNotExist())
                .andExpect(jsonPath("$[0].volunteerCount").value(2))
                .andExpect(jsonPath("$[0].lastActivityAt").isEmpty());
    }

    @Test
    @DisplayName("층과 검색어를 서비스로 넘긴다")
    void floorAndQueryArePassed() throws Exception {
        mockMvc.perform(get(BASE).param("floor", "4").param("q", "412").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk());

        verify(volunteerService).getVolunteers(MEMBER_ID, 4, "412", null, null);
    }

    @Test
    @DisplayName("증가·차감은 경로의 학생 id와 Idempotency-Key로 처리하고 바뀐 항목을 응답한다")
    void adjustReturnsUpdatedItem() throws Exception {
        VolunteerResponse updated = new VolunteerResponse(200L, "학생", 2105, 301, 3, Instant.parse("2026-10-01T03:00:00Z"), null);
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
    @DisplayName("여러 회 조정은 본문의 횟수·사유와 Idempotency-Key를 서비스로 넘기고 바뀐 항목을 응답한다")
    void adjustCountPassesDeltaAndReason() throws Exception {
        VolunteerResponse updated = new VolunteerResponse(200L, "학생", 2105, 301, 0, Instant.parse("2026-10-01T03:00:00Z"), null);
        given(volunteerService.adjustCount(MEMBER_ID, 200L, "key-1", -3, "감면")).willReturn(updated);

        mockMvc.perform(patch(BASE + "/200/count").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-3,\"reason\":\"감면\"}")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volunteerCount").value(0));

        verify(volunteerService).adjustCount(MEMBER_ID, 200L, "key-1", -3, "감면");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"delta\":100}", "{\"delta\":-100}"})
    @DisplayName("여러 회 조정의 횟수가 없거나 -99~99를 벗어나면 400이고 서비스를 호출하지 않는다")
    void adjustCountRejectsInvalidDelta(String body) throws Exception {
        mockMvc.perform(patch(BASE + "/200/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        verify(volunteerService, never()).adjustCount(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("여러 회 조정의 사유가 100자를 넘으면 400이고 서비스를 호출하지 않는다")
    void adjustCountRejectsTooLongReason() throws Exception {
        mockMvc.perform(patch(BASE + "/200/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1,\"reason\":\"" + "가".repeat(101) + "\"}")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        verify(volunteerService, never()).adjustCount(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("조정 이력은 경로의 학생 id와 limit을 서비스로 넘기고 배열로 응답한다")
    void adjustmentsAreReturnedAsArray() throws Exception {
        given(volunteerService.getAdjustments(MEMBER_ID, 200L, 20)).willReturn(List.of(
                new VolunteerAdjustmentResponse(Instant.parse("2026-10-01T03:00:00Z"), -2, -5, "감면",
                        VolunteerAdjustmentKind.ADMIN),
                new VolunteerAdjustmentResponse(Instant.parse("2026-09-30T03:00:00Z"), -1, -1, null,
                        VolunteerAdjustmentKind.DUTY_COMPLETION)));

        mockMvc.perform(get(BASE + "/200/adjustments").param("limit", "20").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-01T03:00:00Z"))
                .andExpect(jsonPath("$[0].delta").value(-2))
                .andExpect(jsonPath("$[0].requestedDelta").value(-5))
                .andExpect(jsonPath("$[0].reason").value("감면"))
                .andExpect(jsonPath("$[0].kind").value("ADMIN"))
                .andExpect(jsonPath("$[1].kind").value("DUTY_COMPLETION"))
                .andExpect(jsonPath("$[1].reason").doesNotExist());
    }

    @Test
    @DisplayName("횟수가 0인데 차감하면 409 VOLUNTEER_COUNT_ZERO로 응답한다")
    void decreaseAtZeroReturnsConflict() throws Exception {
        willThrow(new CustomException(ErrorCode.VOLUNTEER_COUNT_ZERO))
                .given(volunteerService).decrease(MEMBER_ID, 200L, null);

        mockMvc.perform(patch(BASE + "/200/count/decrease").with(loginAs(MEMBER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOLUNTEER_COUNT_ZERO"));
    }

    @Test
    @DisplayName("당일 봉사자 후보 검색용 최소 봉사 횟수를 서비스로 넘긴다")
    void minCountIsPassed() throws Exception {
        mockMvc.perform(get(BASE).param("minCount", "1").param("q", "민우").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk());

        verify(volunteerService).getVolunteers(MEMBER_ID, null, "민우", 1, null);
    }

    @Test
    @DisplayName("지정·취소·완료는 경로의 학생 id로 처리하고 바뀐 항목을 오늘 지정 상태와 함께 응답한다")
    void dutyEndpointsReturnUpdatedItem() throws Exception {
        given(volunteerService.assignDuty(MEMBER_ID, 200L))
                .willReturn(new VolunteerResponse(200L, "학생", 2105, 301, 2, null, DutyStatus.ASSIGNED));
        given(volunteerService.cancelDuty(MEMBER_ID, 200L))
                .willReturn(new VolunteerResponse(200L, "학생", 2105, 301, 2, null, null));
        given(volunteerService.completeDuty(MEMBER_ID, 200L))
                .willReturn(new VolunteerResponse(200L, "학생", 2105, 301, 1, null, DutyStatus.COMPLETED));

        mockMvc.perform(post(BASE + "/200/duty").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayDuty").value("ASSIGNED"));
        mockMvc.perform(delete(BASE + "/200/duty").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayDuty").isEmpty());
        mockMvc.perform(post(BASE + "/200/duty/complete").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayDuty").value("COMPLETED"))
                .andExpect(jsonPath("$.volunteerCount").value(1));
    }

    @Test
    @DisplayName("봉사가 없는 학생을 지정하면 409 NO_VOLUNTEER_LEFT와 \"봉사가 없습니다.\"로 응답한다")
    void assignWithoutVolunteerReturnsConflict() throws Exception {
        willThrow(new CustomException(ErrorCode.NO_VOLUNTEER_LEFT)).given(volunteerService).assignDuty(MEMBER_ID, 200L);

        mockMvc.perform(post(BASE + "/200/duty").with(loginAs(MEMBER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_VOLUNTEER_LEFT"))
                .andExpect(jsonPath("$.message").value("봉사가 없습니다."));
    }

    @Test
    @DisplayName("오늘 봉사자만 보기 필터를 서비스로 넘긴다")
    void onDutyIsPassed() throws Exception {
        mockMvc.perform(get(BASE).param("onDuty", "true").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk());

        verify(volunteerService).getVolunteers(MEMBER_ID, null, null, null, true);
    }

    @Test
    @DisplayName("관리자가 아니면 403 ADMIN_ONLY로 응답한다")
    void nonAdminReturnsForbidden() throws Exception {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(volunteerService).getVolunteers(MEMBER_ID, null, null, null, null);

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/200/count/increase")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/200/count/decrease")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/200/duty")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/200/duty")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/200/duty/complete")).andExpect(status().isUnauthorized());

        verify(volunteerService, never()).getVolunteers(any(), any(), any(), any(), any());
        verify(volunteerService, never()).increase(any(), any(), any());
        verify(volunteerService, never()).decrease(any(), any(), any());
        verify(volunteerService, never()).assignDuty(any(), any());
        verify(volunteerService, never()).cancelDuty(any(), any());
        verify(volunteerService, never()).completeDuty(any(), any());
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
