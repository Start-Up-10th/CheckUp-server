package com.checkup.checkup.domain.attendance.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.checkup.checkup.domain.attendance.entity.Attendance;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * 자동 인증(QR·얼굴)으로 출석을 기록한다. 학생·용도·운영일 유니크 제약으로 DB에서 원자적으로 처리한다.
     *
     * - 행이 없으면 출석으로 만든다.
     * - 이미 출석이면 바꾸지 않는다.
     * - 미출석 행은 마지막 수동 수정 뒤에 발생한 인증일 때만 출석으로 바꾼다(DEC-008).
     *   최초 인증 시각과 방식은 가장 이른 값을 유지한다.
     *
     * 호출하는 쪽 트랜잭션에서 로딩한 엔티티를 비우지 않도록 영속성 컨텍스트는 clear하지 않는다.
     * 그래서 이 쿼리 뒤의 상태 확인은 엔티티가 아닌 {@link #findAttendedStatus}로 DB에서 직접 읽는다.
     *
     * @return 출석으로 새로 기록했으면 1, 이미 출석이었거나 수동 수정 이전 인증이라 무시했으면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO attendance (student_id, purpose, operating_day, attended, first_verified_at, method)
            VALUES (:studentId, :purpose, :operatingDay, TRUE, :verifiedAt, :method)
            ON CONFLICT (student_id, purpose, operating_day) DO UPDATE
            SET attended = TRUE,
                first_verified_at = LEAST(attendance.first_verified_at, EXCLUDED.first_verified_at),
                method = CASE
                    WHEN attendance.first_verified_at IS NULL
                        OR EXCLUDED.first_verified_at < attendance.first_verified_at
                    THEN EXCLUDED.method
                    ELSE attendance.method
                END
            WHERE attendance.attended = FALSE
              AND (attendance.manual_updated_at IS NULL
                   OR attendance.manual_updated_at < EXCLUDED.first_verified_at)
            """, nativeQuery = true)
    int markAttended(
            @Param("studentId") Long studentId,
            @Param("purpose") String purpose,
            @Param("operatingDay") LocalDate operatingDay,
            @Param("verifiedAt") Instant verifiedAt,
            @Param("method") String method
    );

    /**
     * 주어진 학생들 중 해당 용도·운영일에 지금 출석 상태인 학생의 id를 읽는다. 호실 명단의 출석 표시에 쓴다.
     * 행이 없거나 미출석(수동 수정 포함)인 학생은 포함하지 않는다.
     */
    @Query("""
            SELECT a.student.id FROM Attendance a
            WHERE a.student.id IN :studentIds AND a.purpose = :purpose
              AND a.operatingDay = :operatingDay AND a.attended = TRUE
            """)
    List<Long> findAttendedStudentIds(
            @Param("studentIds") Collection<Long> studentIds,
            @Param("purpose") AttendancePurpose purpose,
            @Param("operatingDay") LocalDate operatingDay
    );

    /**
     * 학생·용도·운영일의 현재 출석 여부만 DB에서 읽는다. 영속성 컨텍스트에 캐시된 엔티티를 거치지 않는다.
     */
    @Query("""
            SELECT a.attended FROM Attendance a
            WHERE a.student.id = :studentId AND a.purpose = :purpose AND a.operatingDay = :operatingDay
            """)
    Optional<Boolean> findAttendedStatus(
            @Param("studentId") Long studentId,
            @Param("purpose") AttendancePurpose purpose,
            @Param("operatingDay") LocalDate operatingDay
    );
}
