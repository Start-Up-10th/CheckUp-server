package com.checkup.checkup.domain.volunteer.repository;

import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface VolunteerAdjustmentRepository extends JpaRepository<VolunteerAdjustment, Long> {

    /**
     * 조정 기록을 남긴다. 같은 {@code requestKey}가 이미 있으면 예외 없이 무시한다(재시도).
     * {@code requestKey}가 {@code null}이면 unique 검사 대상이 아니라 항상 저장된다.
     *
     * @return 새로 저장했으면 1, 같은 키가 있어 무시했으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO volunteer_adjustment (student_id, delta, request_key, created_at)
            VALUES (:studentId, :delta, :requestKey, :createdAt)
            ON CONFLICT (request_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("studentId") Long studentId,
            @Param("delta") int delta,
            @Param("requestKey") String requestKey,
            @Param("createdAt") Instant createdAt
    );
}
