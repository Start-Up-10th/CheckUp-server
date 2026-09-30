package com.checkup.checkup.domain.notification.service;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.notification.dto.response.NotificationListResponse;
import com.checkup.checkup.domain.notification.dto.response.NotificationResponse;
import com.checkup.checkup.domain.notification.repository.NotificationRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 학생 본인의 웹 내부 알림을 조회·읽음 처리하고, 다른 서비스가 알림을 만들 수 있게 한다(REQ-COM-005).
 *
 * 조회·읽음 처리는 세션의 회원으로 학생을 찾아 그 학생의 알림만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;

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

    private Long studentIdOf(Long memberId) {
        Student student = studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
        return student.getId();
    }
}
