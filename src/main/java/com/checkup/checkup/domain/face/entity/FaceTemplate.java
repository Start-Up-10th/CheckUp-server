package com.checkup.checkup.domain.face.entity;

import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.member.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Persistent representative vectors and the model metadata needed to interpret them. */
@Getter
@Entity
@Table(name = "face_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(name = "model_id", nullable = false, length = 120)
    private String modelId;

    @Column(name = "model_version", nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private int dimension;

    @Column(nullable = false, length = 20)
    private String normalization;

    @Column(name = "vectors_json", nullable = false, columnDefinition = "TEXT")
    private String vectorsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FaceTemplate create(Student student, AiFaceModel model, String vectorsJson) {
        FaceTemplate template = new FaceTemplate();
        template.student = student;
        template.modelId = model.modelId();
        template.modelVersion = model.version();
        template.dimension = model.dimension();
        template.normalization = model.normalization();
        template.vectorsJson = vectorsJson;
        return template;
    }
}
