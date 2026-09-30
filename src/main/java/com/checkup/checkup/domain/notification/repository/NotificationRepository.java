package com.checkup.checkup.domain.notification.repository;

import com.checkup.checkup.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 학생의 최근 알림 50개를 최신순으로 읽는다. */
    List<Notification> findTop50ByStudentIdOrderByCreatedAtDesc(Long studentId);

    /** 학생에게 읽지 않은 알림이 있는지 확인한다. */
    boolean existsByStudentIdAndReadAtIsNull(Long studentId);
}
