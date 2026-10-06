package com.checkup.checkup.domain.volunteer.repository;

import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 봉사 횟수 조정 기록(REQ-COM-002). 관리자 조정과 당일 봉사 완료를 종류로 구분한다. */
public interface VolunteerAdjustmentRepository extends JpaRepository<VolunteerAdjustment, Long> {

    /**
     * 조정 기록을 남긴다. 같은 {@code requestKey}가 이미 있으면 예외 없이 무시한다(재시도).
     * {@code requestKey}가 {@code null}이면 unique 검사 대상이 아니라 항상 저장된다.
     *
     * @param delta          실제로 바뀐 횟수
     * @param requestedDelta 관리자가 요청한 횟수
     * @param reason         사유. 없으면 {@code null}
     * @param kind           조정 종류. {@code ADMIN} 또는 {@code DUTY_COMPLETION}
     * @return 새로 저장했으면 1, 같은 키가 있어 무시했으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO volunteer_adjustment (student_id, delta, requested_delta, reason, kind, request_key, created_at)
            VALUES (:studentId, :delta, :requestedDelta, CAST(:reason AS VARCHAR), :kind, :requestKey, :createdAt)
            ON CONFLICT (request_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("studentId") Long studentId,
            @Param("delta") int delta,
            @Param("requestedDelta") int requestedDelta,
            @Param("reason") String reason,
            @Param("kind") String kind,
            @Param("requestKey") String requestKey,
            @Param("createdAt") Instant createdAt
    );

    /**
     * 학생 한 명의 조정 기록을 최신순으로 읽는다. 같은 시각이면 나중에 저장한 기록이 앞이다. 봉사 조정 이력(#140)에 쓴다.
     *
     * @param studentId 학생 DB id
     * @param limit     최대 개수
     */
    List<VolunteerAdjustment> findByStudent_IdOrderByCreatedAtDescIdDesc(Long studentId, Limit limit);

    /** 재시도 키로 이미 남은 조정 기록을 찾는다. 같은 키가 다른 학생·방향에 쓰였는지 확인할 때 쓴다. */
    Optional<VolunteerAdjustment> findByRequestKey(String requestKey);

    /**
     * 학생별 마지막 조정 시각을 읽는다. 봉사 관리 명단의 "최근 활동"이다. 조정한 적 없는 학생은 결과에 없다.
     */
    @Query("""
            SELECT a.student.id AS studentId, MAX(a.createdAt) AS lastActivityAt
            FROM VolunteerAdjustment a
            GROUP BY a.student.id
            """)
    List<LastActivity> findLastActivities();

    /** 학생 한 명의 마지막 조정 시각. 조정한 적 없으면 {@code null}이다. */
    @Query("SELECT MAX(a.createdAt) FROM VolunteerAdjustment a WHERE a.student.id = :studentId")
    Instant findLastActivityAt(@Param("studentId") Long studentId);

    /** 학생별 마지막 조정 시각 조회 결과. */
    interface LastActivity {
        Long getStudentId();

        Instant getLastActivityAt();
    }
}
