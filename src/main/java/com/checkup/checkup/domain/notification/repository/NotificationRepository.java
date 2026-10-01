package com.checkup.checkup.domain.notification.repository;

import com.checkup.checkup.domain.notification.entity.Notification;
import com.checkup.checkup.domain.notification.entity.NotificationType;
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

    /**
     * 알림을 저장한다. 같은 학생·유형·원본의 알림이 이미 있으면 예외 없이 무시한다.
     * {@code save()}로 중복을 넣으면 unique 위반으로 호출한 쪽 트랜잭션(출석 저장 등)까지 롤백되므로 이 쿼리를 쓴다.
     *
     * @return 새로 저장했으면 1, 이미 있어 무시했으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification (student_id, type, source_key, message, created_at)
            VALUES (:studentId, :type, :sourceKey, :message, :createdAt)
            ON CONFLICT (student_id, type, source_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("studentId") Long studentId,
            @Param("type") String type,
            @Param("sourceKey") String sourceKey,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt
    );

    /**
     * 주어진 시각 이전에 만든 해당 유형의 알림을 한 번에 지운다. 08:00 KST 출석 알림 폐기에 쓴다.
     *
     * @return 지운 알림 수
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.type = :type AND n.createdAt < :before")
    int deleteByTypeBefore(
            @Param("type") NotificationType type,
            @Param("before") Instant before
    );

    /**
     * 학생의 특정 알림 하나를 지운다. 당일 봉사자 지정을 취소할 때 그 지정 알림을 지우는 데 쓴다.
     *
     * @return 지운 알림 수
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.student.id = :studentId AND n.type = :type AND n.sourceKey = :sourceKey")
    int deleteOne(
            @Param("studentId") Long studentId,
            @Param("type") NotificationType type,
            @Param("sourceKey") String sourceKey
    );
}
