package com.checkup.checkup.domain.face.entity;

import com.checkup.checkup.domain.member.entity.Student;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "face_recognition_session_candidate", uniqueConstraints =
        @UniqueConstraint(name = "uk_face_session_candidate", columnNames = {"session_id", "student_id"}))
/** 얼굴 인식 세션이 대조하는 후보 학생 한 명. 세션을 만들 때 출석 대상이고 얼굴을 등록한 학생이다. */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceRecognitionSessionCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private FaceRecognitionSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    public static FaceRecognitionSessionCandidate create(FaceRecognitionSession session, Student student) {
        FaceRecognitionSessionCandidate candidate = new FaceRecognitionSessionCandidate();
        candidate.session = session;
        candidate.student = student;
        return candidate;
    }
}
