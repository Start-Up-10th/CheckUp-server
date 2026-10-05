package com.checkup.checkup.domain.attendance.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.attendance.dto.response.MyAttendanceResponse;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.GlobalExceptionHandler;
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
 * 본인 출석 조회 API가 로그인한 회원 id로 서비스를 부르고, 용도별 출석 상태를 JSON 필드로 응답하며
 * 미로그인·학생 아님 오류를 공통 형식으로 돌려주는지 검증한다.
 */
@WebMvcTest(AttendanceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AttendanceControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ME = "/api/v1/attendance/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

    @Test
    @DisplayName("본인의 오늘 출석 상태를 운영일과 용도별 상태로 반환한다")
    void returnsMyAttendanceByPurpose() throws Exception {
        given(attendanceService.getMyToday(MEMBER_ID)).willReturn(new MyAttendanceResponse(
                LocalDate.of(2026, 10, 5),
                new MyAttendanceResponse.Status(true, Instant.parse("2026-10-05T12:00:00Z")),
                new MyAttendanceResponse.Status(false, null)));

        mockMvc.perform(get(ME).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingDay").value("2026-10-05"))
                .andExpect(jsonPath("$.dormitory.attended").value(true))
                .andExpect(jsonPath("$.dormitory.firstVerifiedAt").value("2026-10-05T12:00:00Z"))
                .andExpect(jsonPath("$.studyRoom.attended").value(false))
                .andExpect(jsonPath("$.studyRoom.firstVerifiedAt").isEmpty());
    }

    @Test
    @DisplayName("학생이 아니면 403 MISSING_STUDENT_INFO를 반환한다")
    void nonStudentReturnsForbidden() throws Exception {
        given(attendanceService.getMyToday(MEMBER_ID))
                .willThrow(new CustomException(ErrorCode.MISSING_STUDENT_INFO));

        mockMvc.perform(get(ME).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    @Test
    @DisplayName("미인증 요청은 서비스를 호출하지 않고 401을 반환한다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(attendanceService);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
