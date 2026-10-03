package com.checkup.checkup.domain.face.controller;

import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.domain.face.service.FaceStudentService;
import com.checkup.checkup.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceController.class)
@Import(SecurityConfig.class)
class FaceControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaceStudentService faceStudentService;

    @MockitoBean
    private FaceEnrollmentService faceEnrollmentService;

    @Test
    @DisplayName("로그인하지 않은 얼굴 요청은 공통 401로 차단한다")
    void unauthenticatedFaceRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/face/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
