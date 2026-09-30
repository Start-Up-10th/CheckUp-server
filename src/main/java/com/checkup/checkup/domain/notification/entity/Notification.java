package com.checkup.checkup.domain.notification.entity;

import java.time.Instant;

import com.checkup.checkup.domain.member.entity.Student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생에게 보이는 웹 내부 알림 한 건(REQ-COM-005).
 *
 * 같은 학생·유형·원본({@code sourceKey})에는 한 건만 있다. 출석 알림의 원본은 {@code 용도:운영일}이다.
 * {@code readAt}이 {@code null}이면 아직 읽지 않은 알림이다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "notification")
@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String sourceKey;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    /**
     * 읽지 않은 새 알림을 만든다.
     *
     * @param student   알림을 받는 학생
     * @param type      알림 유형
     * @param sourceKey 알림 원본. 같은 학생·유형·원본의 알림은 하나만 저장된다.
     * @param message   화면에 표시할 문구
     * @param createdAt 만든 시각
     */
    public static Notification create(
            Student student,
            NotificationType type,
            String sourceKey,
            String message,
            Instant createdAt) {
        Notification notification = new Notification();
        notification.student = student;
        notification.type = type;
        notification.sourceKey = sourceKey;
        notification.message = message;
        notification.createdAt = createdAt;
        return notification;
    }

}
