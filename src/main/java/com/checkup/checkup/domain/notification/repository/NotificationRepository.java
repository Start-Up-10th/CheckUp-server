package com.checkup.checkup.domain.notification.repository;

import com.checkup.checkup.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 학생의 최근 알림 50개를 최신순으로 읽는다. */
    List<Notification> findTop50ByStudentIdOrderByCreatedAtDesc(Long studentId);

    /** 학생에게 읽지 않은 알림이 있는지 확인한다. */
    boolean existsByStudentIdAndReadAtIsNull(Long studentId);

    /**
     * 학생의 읽지 않은 알림을 모두 읽음으로 바꾼다. 이미 읽은 알림의 읽은 시각은 덮어쓰지 않는다.
     *
     * @return 읽음으로 바꾼 알림 수
     */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.student.id = :studentId AND n.readAt IS NULL
            """)
    int markAllRead(
            @Param("studentId") Long studentId,
            @Param("readAt") Instant readAt
    );
}
