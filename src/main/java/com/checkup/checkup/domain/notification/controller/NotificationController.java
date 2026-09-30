package com.checkup.checkup.domain.notification.controller;

import com.checkup.checkup.domain.notification.dto.response.NotificationListResponse;
import com.checkup.checkup.domain.notification.dto.response.UnreadResponse;
import com.checkup.checkup.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 학생 본인의 웹 내부 알림 API(REQ-COM-005). 로그인한 학생의 알림만 다루며 다른 학생 id는 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 본인 알림 최근 50개를 최신순으로 조회한다. 읽음 상태는 바꾸지 않는다.
     *
     * @param memberId 세션의 회원 id
     */
    @GetMapping
    public NotificationListResponse getNotifications(@AuthenticationPrincipal Long memberId) {
        return notificationService.getNotifications(memberId);
    }

    /**
     * 본인에게 읽지 않은 알림이 있는지 조회한다.
     *
     * @param memberId 세션의 회원 id
     */
    @GetMapping("/unread")
    public UnreadResponse hasUnread(@AuthenticationPrincipal Long memberId) {
        return notificationService.hasUnread(memberId);
    }

    /**
     * 본인의 읽지 않은 알림을 모두 읽음으로 바꾼다. 웹이 알림 목록 화면에 들어올 때 호출한다.
     *
     * @param memberId 세션의 회원 id
     */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal Long memberId) {
        notificationService.readAll(memberId);
    }
}
