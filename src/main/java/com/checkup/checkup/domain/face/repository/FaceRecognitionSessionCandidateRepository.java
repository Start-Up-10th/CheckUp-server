package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.face.entity.FaceRecognitionSessionCandidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 얼굴 인식 세션의 후보 학생 목록. */
public interface FaceRecognitionSessionCandidateRepository
        extends JpaRepository<FaceRecognitionSessionCandidate, Long> {

    @EntityGraph(attributePaths = "student")
    List<FaceRecognitionSessionCandidate> findAllBySession_Id(UUID sessionId);

    @Modifying
    @Query("DELETE FROM FaceRecognitionSessionCandidate c WHERE c.session.id = :sessionId")
    void deleteAllForSession(@Param("sessionId") UUID sessionId);

    List<FaceRecognitionSessionCandidate> findAllByStudent_Id(Long studentId);

    /**
     * 주어진 학생 모두를 세션의 후보로 한 문장에 저장한다. 후보 수만큼 insert하지 않는다(#216).
     * 저장되지 않은 학생 id는 건너뛰므로 반환값이 id 수보다 작으면 그런 id가 있다는 뜻이다.
     *
     * @return 저장한 후보 수
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO face_recognition_session_candidate (session_id, student_id)
            SELECT :sessionId, s.id FROM student s WHERE s.id IN (:studentIds)
            """, nativeQuery = true)
    int insertAll(@Param("sessionId") UUID sessionId, @Param("studentIds") Collection<Long> studentIds);
}
