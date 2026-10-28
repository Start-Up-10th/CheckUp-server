package com.checkup.checkup.domain.face.entity;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 출석 페이지와 그 페이지의 AI 세션 id를 잇는, Spring이 관리하는 매핑이다. */
@Getter
@Entity
@Table(name = "face_recognition_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceRecognitionSession {
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID id;

    @Column(name = "admin_member_id", nullable = false)
    private Long adminMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendancePurpose purpose;

    /** 이 카메라가 맡은 층(3·4·5). 층 도입 전에 만든 세션은 null이다. */
    @Column
    private Integer floor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /** 마지막 프레임 처리를 시작한 시각. 프레임 최소 간격을 이 시각부터 센다(#262). 아직 프레임이 없으면 null이다. */
    @Column(name = "last_frame_started_at")
    private Instant lastFrameStartedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "frame_lock_token")
    private UUID frameLockToken;

    @Column(name = "frame_lock_until")
    private Instant frameLockUntil;

    public static FaceRecognitionSession create(
            UUID id,
            Long adminMemberId,
            AttendancePurpose purpose,
            Integer floor,
            Instant createdAt,
            Instant lastActivityAt
    ) {
        FaceRecognitionSession session = new FaceRecognitionSession();
        session.id = id;
        session.adminMemberId = adminMemberId;
        session.purpose = purpose;
        session.floor = floor;
        session.createdAt = createdAt;
        session.lastActivityAt = lastActivityAt;
        session.active = true;
        return session;
    }
}
