package com.checkup.checkup.domain.face.entity;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 관리자 얼굴 출석 화면의 최근 인식 기록 한 줄(#143, REQ-ATT-007). 지난 운영일 기록은 08:00 KST에 지운다.
 * 얼굴 이미지·좌표·벡터는 저장하지 않는다. 저장은
 * {@link com.checkup.checkup.domain.face.repository.FaceRecognitionLogRepository#insertIfAbsent}로만 한다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "face_recognition_log")
@Entity
public class FaceRecognitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "admin_member_id", nullable = false)
    private Long adminMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendancePurpose purpose;

    @Column(name = "operating_day", nullable = false)
    private LocalDate operatingDay;

    @Column(name = "track_id", nullable = false, length = 128)
    private String trackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FaceRecognitionResult result;

    /** 알아본 학생. 못 알아본 얼굴이면 {@code null}이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "recognized_at", nullable = false)
    private Instant recognizedAt;
}
