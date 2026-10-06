package com.checkup.checkup.domain.face.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.dto.FaceRecognitionLogResponse;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.face.service.FaceRecognitionLogService;
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

/**
 * 최근 인식 목록 API가 용도·개수를 서비스로 넘기고, 못 알아본 얼굴은 이름 없이 응답하는지 검증한다(#143).
 */
@WebMvcTest(FaceRecognitionLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FaceRecognitionLogControllerTest {

    private static final Long ADMIN_ID = 12L;
    private static final String PATH = "/api/v1/face/recognitions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaceRecognitionLogService faceRecognitionLogService;

    @Test
    @DisplayName("용도와 개수를 서비스로 넘기고 최근 인식을 배열로 응답한다")
    void recentLogsAreReturned() throws Exception {
        Instant at = Instant.parse("2026-10-06T12:00:00Z");
        given(faceRecognitionLogService.getRecent(ADMIN_ID, AttendancePurpose.DORMITORY, 20)).willReturn(List.of(
                new FaceRecognitionLogResponse(at, FaceRecognitionResult.SUCCESS, "학생", 2105),
                new FaceRecognitionLogResponse(at, FaceRecognitionResult.FAILED, null, null)));

        mockMvc.perform(get(PATH).param("purpose", "DORMITORY").param("limit", "20")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(ADMIN_ID, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$[0].studentName").value("학생"))
                .andExpect(jsonPath("$[0].studentNumber").value(2105))
                .andExpect(jsonPath("$[1].result").value("FAILED"))
                .andExpect(jsonPath("$[1].studentName").doesNotExist());
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get(PATH).param("purpose", "DORMITORY"))
                .andExpect(status().isUnauthorized());

        verify(faceRecognitionLogService, never()).getRecent(any(), any(), any());
    }
}
