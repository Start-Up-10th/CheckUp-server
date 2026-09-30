package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.face.entity.FaceRecognitionSessionCandidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FaceRecognitionSessionCandidateRepository
        extends JpaRepository<FaceRecognitionSessionCandidate, Long> {

    @EntityGraph(attributePaths = "student")
    List<FaceRecognitionSessionCandidate> findAllBySession_Id(UUID sessionId);

    @Modifying
    @Query("DELETE FROM FaceRecognitionSessionCandidate c WHERE c.session.id = :sessionId")
    void deleteAllForSession(@Param("sessionId") UUID sessionId);

    List<FaceRecognitionSessionCandidate> findAllByStudent_Id(Long studentId);
}
