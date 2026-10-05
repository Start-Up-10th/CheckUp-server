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
     * 관리자 수동 수정으로 학생을 출석 상태로 바꾼다(REQ-ATT-006). 지금 미출석이거나 행이 없을 때만 바꾼다.
     *
     * 최초 유효 인증 시각은 건드리지 않는다. 자동 인증 없이 수동으로만 출석한 학생은 그 시각이 비어 있다.
     * 인증 방식은 이미 있으면 유지하고 없을 때만 MANUAL로 둔다.
     *
     * @return 출석으로 바꿨으면 1, 이미 출석이라 그대로면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO attendance (student_id, purpose, operating_day, attended, method, manual_updated_at)
            VALUES (:studentId, :purpose, :operatingDay, TRUE, 'MANUAL', :updatedAt)
            ON CONFLICT (student_id, purpose, operating_day) DO UPDATE
            SET attended = TRUE,
                method = COALESCE(attendance.method, 'MANUAL'),
                manual_updated_at = EXCLUDED.manual_updated_at
            WHERE attendance.attended = FALSE
            """, nativeQuery = true)
    int markManuallyAttended(
            @Param("studentId") Long studentId,
            @Param("purpose") String purpose,
            @Param("operatingDay") LocalDate operatingDay,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * 관리자 수동 수정으로 학생을 미출석 상태로 바꾼다(REQ-ATT-006). 지금 출석 상태일 때만 바꾼다.
     *
     * 행을 지우지 않고 최초 유효 인증 시각과 방식을 남긴다. 수정 시각을 기록해 그 전에 발생한 인증이
     * 늦게 도착해도 다시 출석으로 바뀌지 않게 한다(DEC-008). 출석 행이 없는 학생은 이미 미출석이라 행을 만들지 않는다.
     *
     * @return 미출석으로 바꿨으면 1, 이미 미출석이라 그대로면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE attendance
            SET attended = FALSE, manual_updated_at = :updatedAt
            WHERE student_id = :studentId AND purpose = :purpose
              AND operating_day = :operatingDay AND attended = TRUE
            """, nativeQuery = true)
    int markManuallyAbsent(
            @Param("studentId") Long studentId,
            @Param("purpose") String purpose,
            @Param("operatingDay") LocalDate operatingDay,
            @Param("updatedAt") Instant updatedAt
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
     * 호실 번호가 주어진 범위에 있는 호실마다 배정 인원과 해당 용도·운영일의 출석 인원을 한 번에 센다.
     * 관리자 전개도의 층 단위 현황에 쓴다. 호실 번호 오름차순이다.
     *
     * 배정 인원은 호실이 배정된 학생 수라 4명으로 고정하지 않는다(REQ-UI-001). 호실이 없는 학생은 세지 않는다.
     * 출석 행이 없거나 미출석(수동 수정 포함)인 학생은 배정 인원에만 들어간다.
     * 학생·용도·운영일마다 출석 행이 하나라 학생이 두 번 세어지지 않는다.
     */
    @Query("""
            SELECT s.dormitoryRoom AS dormitoryRoom, COUNT(s) AS assigned, COUNT(a) AS attended
            FROM Student s
            LEFT JOIN Attendance a
              ON a.student = s AND a.purpose = :purpose
             AND a.operatingDay = :operatingDay AND a.attended = TRUE
            WHERE s.dormitoryRoom BETWEEN :firstRoom AND :lastRoom
            GROUP BY s.dormitoryRoom
            ORDER BY s.dormitoryRoom
            """)
    List<RoomAttendanceCount> countByRoom(
            @Param("firstRoom") int firstRoom,
            @Param("lastRoom") int lastRoom,
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

    /**
     * 주어진 운영일 전의 출석 행을 용도와 상관없이 한 번에 지운다. 08:00 KST 당일 기록 폐기에 쓴다(REQ-ATT-007).
     * 주어진 운영일의 행은 남긴다.
     *
     * @return 지운 행 수
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Attendance a WHERE a.operatingDay < :operatingDay")
    int deleteByOperatingDayBefore(@Param("operatingDay") LocalDate operatingDay);
}
