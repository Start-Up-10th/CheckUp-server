package com.checkup.checkup.domain.notification.service;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.notification.dto.response.NotificationListResponse;
import com.checkup.checkup.domain.notification.dto.response.NotificationResponse;
import com.checkup.checkup.domain.notification.dto.response.UnreadResponse;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.notification.repository.NotificationRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * 학생 본인의 웹 내부 알림을 조회·읽음 처리하고, 다른 서비스가 알림을 만들 수 있게 한다(REQ-COM-005).
 *
 * 조회·읽음 처리는 세션의 회원으로 학생을 찾아 그 학생의 알림만 다룬다.
 * 시각은 주입한 {@link Clock}으로 정해 테스트에서 고정할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    /**
     * 본인 알림 최근 50개를 최신순으로 읽고 읽지 않은 알림이 있는지 함께 돌려준다. 읽음 상태는 바꾸지 않는다.
     *
     * @param memberId 세션의 회원 id
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long memberId) {
        Long studentId = studentIdOf(memberId);
        List<NotificationResponse> result = notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        boolean hasUnread = notificationRepository.existsByStudentIdAndReadAtIsNull(studentId);
        return new NotificationListResponse(hasUnread, result);
    }

    /**
     * 본인에게 읽지 않은 알림이 있는지 돌려준다. 홈 헤더 벨·사이드바 강조에 쓴다.
     *
     * @param memberId 세션의 회원 id
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional(readOnly = true)
    public UnreadResponse hasUnread(Long memberId) {
        Long studentId = studentIdOf(memberId);
        return new UnreadResponse(notificationRepository.existsByStudentIdAndReadAtIsNull(studentId));
    }

    /**
     * 본인의 읽지 않은 알림을 모두 읽음으로 바꾼다. 웹이 알림 목록 화면에 들어올 때 호출한다.
     *
     * @param memberId 세션의 회원 id
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional
    public void readAll(Long memberId) {
        Long studentId = studentIdOf(memberId);
        notificationRepository.markAllRead(studentId, clock.instant());
    }

    /**
     * 알림을 만든다. 같은 학생·유형·원본의 알림이 이미 있으면 아무것도 하지 않는다.
     * 호출한 쪽 트랜잭션(출석 저장 등)에 합류하므로 원본 처리가 취소되면 알림도 남지 않는다.
     *
     * @param studentId 알림을 받는 학생 id
     * @param type      알림 유형
     * @param sourceKey 알림 원본. 출석은 {@code 용도:운영일}
     * @param message   화면에 표시할 문구
     */
    @Transactional
    public void create(Long studentId, NotificationType type, String sourceKey, String message) {
        notificationRepository.insertIfAbsent(studentId, type.name(), sourceKey, message, clock.instant());
    }

    private Long studentIdOf(Long memberId) {
        Student student = studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
        return student.getId();
    }
}
