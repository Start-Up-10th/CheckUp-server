package com.checkup.checkup.domain.room.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.domain.room.service.RoomService;
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

/**
 * 호실 명단 API가 계약 필드로 응답하고, 파라미터 오류·미로그인·권한 오류를 공통 형식으로 응답하는지 검증한다.
 */
@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RoomControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @Test
    @DisplayName("호실 학생 명단을 계약에 정의된 JSON 필드의 배열로 반환한다")
    void returnsRoomStudentsWithContractFields() throws Exception {
        given(roomService.getStudents(MEMBER_ID, 301, AttendancePurpose.DORMITORY))
                .willReturn(List.of(new RoomStudentResponse("학생", 1, 1, 1101, true)));

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].student_name").value("학생"))
                .andExpect(jsonPath("$[0].student_grade").value(1))
                .andExpect(jsonPath("$[0].student_class").value(1))
                .andExpect(jsonPath("$[0].student_number").value(1101))
                .andExpect(jsonPath("$[0].attended").value(true))
                .andExpect(jsonPath("$.student_name").doesNotExist());
    }

    @Test
    @DisplayName("미인증 요청은 서비스를 호출하지 않고 401을 반환한다")
    void withoutLoginReturnsUnauthorizedWithoutService() throws Exception {
        mockMvc.perform(get("/api/v1/room/student").param("dormitoryRoom", "301"))
                .andExpect(status().isUnauthorized());

        verify(roomService, never()).getStudents(MEMBER_ID, 301, AttendancePurpose.DORMITORY);
    }

    @Test
    @DisplayName("호실 파라미터가 누락되거나 숫자가 아니거나 0 이하이면 400을 반환한다")
    void invalidRoomParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/room/student").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "abc")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "0")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        verify(roomService, never()).getStudents(MEMBER_ID, 0, AttendancePurpose.DORMITORY);
    }

    @Test
    @DisplayName("서비스의 권한 오류를 기존 오류 응답 형식으로 반환한다")
    void serviceErrorUsesErrorResponse() throws Exception {
        given(roomService.getStudents(MEMBER_ID, 301, AttendancePurpose.DORMITORY))
                .willThrow(new CustomException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("용도를 주면 그 용도의 출석 여부로 조회한다")
    void purposeParameterIsPassedToService() throws Exception {
        given(roomService.getStudents(MEMBER_ID, 301, AttendancePurpose.STUDY_ROOM))
                .willReturn(List.of(new RoomStudentResponse("학생", 1, 1, 1101, false)));

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "301")
                        .param("purpose", "STUDY_ROOM")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attended").value(false));
    }

    @Test
    @DisplayName("없는 용도는 400을 반환한다")
    void invalidPurposeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "301")
                        .param("purpose", "GYM")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
