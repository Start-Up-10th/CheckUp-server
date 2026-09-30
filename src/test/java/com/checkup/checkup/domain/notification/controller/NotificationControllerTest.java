package com.checkup.checkup.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.notification.dto.response.NotificationListResponse;
import com.checkup.checkup.domain.notification.dto.response.NotificationResponse;
import com.checkup.checkup.domain.notification.dto.response.UnreadResponse;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.notification.service.NotificationService;
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
 * 알림 API가 로그인한 회원 id로 서비스를 부르고, 계약의 JSON 필드·상태 코드로 응답하는지 검증한다.
 */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String BASE = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("알림 목록을 hasUnread와 notifications 필드로 응답한다")
    void listReturnsContractFields() throws Exception {
        given(notificationService.getNotifications(MEMBER_ID)).willReturn(new NotificationListResponse(true, List.of(
                new NotificationResponse(5L, NotificationType.ATTENDANCE, "자습실 출석이 완료됐어요",
                        Instant.parse("2026-09-30T03:00:00Z"), false))));

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnread").value(true))
                .andExpect(jsonPath("$.notifications[0].id").value(5))
                .andExpect(jsonPath("$.notifications[0].type").value("ATTENDANCE"))
                .andExpect(jsonPath("$.notifications[0].message").value("자습실 출석이 완료됐어요"))
                .andExpect(jsonPath("$.notifications[0].createdAt").value("2026-09-30T03:00:00Z"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    @Test
    @DisplayName("미확인 여부를 hasUnread로 응답한다")
    void unreadReturnsHasUnread() throws Exception {
        given(notificationService.hasUnread(MEMBER_ID)).willReturn(new UnreadResponse(true));

        mockMvc.perform(get(BASE + "/unread").with(loginAs(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnread").value(true));
    }

    @Test
    @DisplayName("전체 읽음은 204로 응답하고 로그인한 회원 id로 처리한다")
    void readAllReturnsNoContent() throws Exception {
        mockMvc.perform(post(BASE + "/read").with(loginAs(MEMBER_ID)))
                .andExpect(status().isNoContent());

        verify(notificationService).readAll(MEMBER_ID);
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/unread")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/read")).andExpect(status().isUnauthorized());

        verify(notificationService, never()).getNotifications(any());
        verify(notificationService, never()).hasUnread(any());
        verify(notificationService, never()).readAll(any());
    }

    @Test
    @DisplayName("학생이 아닌 계정은 403 MISSING_STUDENT_INFO로 응답한다")
    void nonStudentReturnsForbidden() throws Exception {
        willThrow(new CustomException(ErrorCode.MISSING_STUDENT_INFO))
                .given(notificationService).getNotifications(MEMBER_ID);

        mockMvc.perform(get(BASE).with(loginAs(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
