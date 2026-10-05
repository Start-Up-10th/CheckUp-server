package com.checkup.checkup.domain.room.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.room.dto.request.RoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.RoomFloorResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 호실 명단 API가 계약 필드로 응답하고, 파라미터 오류·미로그인·권한 오류를 공통 형식으로 응답하는지 검증한다.
 * 층 현황 API의 응답 필드, 기본 용도, 관리자 전용 오류, 파라미터 검증도 검증한다.
 * 수동 출석 저장 API가 요청을 서비스로 넘기고 204로 응답하며 본문 검증·호실 불일치 오류를 돌려주는지도 검증한다.
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
                .willReturn(List.of(new RoomStudentResponse(20L, "학생", 1, 1, 1101, true)));

        mockMvc.perform(get("/api/v1/room/student")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].student_id").value(20))
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
                .willReturn(List.of(new RoomStudentResponse(20L, "학생", 1, 1, 1101, false)));

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

    @Test
    @DisplayName("층 현황을 층 전체 인원과 호실별 인원으로 반환한다")
    void returnsFloorWithRoomCounts() throws Exception {
        given(roomService.getFloor(MEMBER_ID, 3, AttendancePurpose.STUDY_ROOM))
                .willReturn(new RoomFloorResponse(3, AttendancePurpose.STUDY_ROOM, 5, 2, List.of(
                        new RoomFloorResponse.Room(301, 4, 4),
                        new RoomFloorResponse.Room(302, 1, 3))));

        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .param("purpose", "STUDY_ROOM")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor").value(3))
                .andExpect(jsonPath("$.purpose").value("STUDY_ROOM"))
                .andExpect(jsonPath("$.attended").value(5))
                .andExpect(jsonPath("$.absent").value(2))
                .andExpect(jsonPath("$.rooms.length()").value(2))
                .andExpect(jsonPath("$.rooms[1].dormitoryRoom").value(302))
                .andExpect(jsonPath("$.rooms[1].attended").value(1))
                .andExpect(jsonPath("$.rooms[1].assigned").value(3));
    }

    @Test
    @DisplayName("층 현황은 용도를 주지 않으면 기숙사 입소로 조회한다")
    void floorDefaultsToDormitoryPurpose() throws Exception {
        given(roomService.getFloor(MEMBER_ID, 4, AttendancePurpose.DORMITORY))
                .willReturn(new RoomFloorResponse(4, AttendancePurpose.DORMITORY, 0, 0, List.of()));

        mockMvc.perform(get("/api/v1/room/floor").param("floor", "4").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("DORMITORY"))
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @Test
    @DisplayName("관리자가 아니면 층 현황은 403 ADMIN_ONLY를 반환한다")
    void floorForNonAdminReturnsAdminOnly() throws Exception {
        given(roomService.getFloor(MEMBER_ID, 3, AttendancePurpose.DORMITORY))
                .willThrow(new CustomException(ErrorCode.ADMIN_ONLY));

        mockMvc.perform(get("/api/v1/room/floor").param("floor", "3").with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("층 현황은 미인증이면 401, 층이 없거나 범위를 벗어나거나 용도가 잘못되면 400을 반환한다")
    void floorRejectsUnauthenticatedAndInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/v1/room/floor").param("floor", "3"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/room/floor").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/room/floor").param("floor", "0").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/room/floor").param("floor", "100").with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .param("purpose", "GYM")
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("수동 출석 저장은 호실·용도·학생별 상태를 서비스로 넘기고 204를 반환한다")
    void savesManualAttendance() throws Exception {
        mockMvc.perform(put("/api/v1/room/301/attendance")
                        .param("purpose", "STUDY_ROOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": [{"studentId": 20, "attended": true}, {"studentId": 21, "attended": false}]}
                                """)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isNoContent());

        verify(roomService).saveAttendance(MEMBER_ID, 301, AttendancePurpose.STUDY_ROOM,
                new RoomAttendanceRequest(List.of(
                        new RoomAttendanceRequest.Item(20L, true),
                        new RoomAttendanceRequest.Item(21L, false))));
    }

    @Test
    @DisplayName("수동 출석 저장에서 그 호실 학생이 아니면 400 STUDENT_NOT_IN_ROOM을 반환한다")
    void manualAttendanceForOtherRoomStudentReturnsBadRequest() throws Exception {
        willThrow(new CustomException(ErrorCode.STUDENT_NOT_IN_ROOM)).given(roomService)
                .saveAttendance(MEMBER_ID, 301, AttendancePurpose.DORMITORY,
                        new RoomAttendanceRequest(List.of(new RoomAttendanceRequest.Item(99L, true))));

        mockMvc.perform(put("/api/v1/room/301/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": [{"studentId": 99, "attended": true}]}
                                """)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_IN_ROOM"));
    }

    @Test
    @DisplayName("수동 출석 저장은 미인증이면 401, 학생 목록이 비었거나 값이 빠지면 400을 반환하고 저장하지 않는다")
    void manualAttendanceRejectsUnauthenticatedAndInvalidBody() throws Exception {
        mockMvc.perform(put("/api/v1/room/301/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": [{"studentId": 20, "attended": true}]}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/room/301/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": []}
                                """)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/room/301/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": [{"studentId": 20}]}
                                """)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/room/0/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"students": [{"studentId": 20, "attended": true}]}
                                """)
                        .with(loginAs(MEMBER_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roomService);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
