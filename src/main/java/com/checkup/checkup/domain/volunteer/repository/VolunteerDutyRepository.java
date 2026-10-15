package com.checkup.checkup.domain.volunteer.repository;

import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 당일 봉사자 지정(REQ-COM-006). 학생·운영일마다 한 행이다. */
public interface VolunteerDutyRepository extends JpaRepository<VolunteerDuty, Long> {

    /**
     * 학생을 그 운영일의 봉사자로 지정한다. 이미 지정돼 있으면 예외 없이 무시한다.
     *
     * @return 새로 지정했으면 1, 이미 지정돼 있으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO volunteer_duty (student_id, operating_day, status, created_at)
            VALUES (:studentId, :operatingDay, 'ASSIGNED', :createdAt)
            ON CONFLICT (student_id, operating_day) DO NOTHING
            """, nativeQuery = true)
    int assign(
            @Param("studentId") Long studentId,
            @Param("operatingDay") LocalDate operatingDay,
            @Param("createdAt") Instant createdAt
    );

    /** 학생의 그 운영일 지정. */
    Optional<VolunteerDuty> findByStudentIdAndOperatingDay(Long studentId, LocalDate operatingDay);

    /**
     * 학생의 그 운영일 지정을 잠가 읽는다. 미이행 처리가 같은 지정에 겹쳐 오면 차례로 처리한다.
     * 완료 처리와 같은 순서(지정 → 학생)로 잠가 서로 기다리다 멈추지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM VolunteerDuty d WHERE d.student.id = :studentId AND d.operatingDay = :operatingDay")
    Optional<VolunteerDuty> lockByStudentIdAndOperatingDay(
            @Param("studentId") Long studentId,
            @Param("operatingDay") LocalDate operatingDay
    );

    /** 그 운영일의 모든 지정을 학생과 함께 읽는다. 날짜별 당일 봉사자 기록(#250)에 쓴다. */
    @Query("SELECT d FROM VolunteerDuty d JOIN FETCH d.student WHERE d.operatingDay = :operatingDay")
    List<VolunteerDuty> findAllWithStudentByOperatingDay(@Param("operatingDay") LocalDate operatingDay);

    /**
     * 기간 안에서 미이행이 있는 운영일과 그날의 미이행 인원을 날짜순으로 센다(#250).
     * 미이행으로 처리한 지정과, {@code today} 전 운영일인데 아직 지정 상태인 지정을 함께 센다.
     */
    @Query("""
            SELECT d.operatingDay AS operatingDay, COUNT(d) AS missedCount
            FROM VolunteerDuty d
            WHERE d.operatingDay BETWEEN :from AND :to
              AND (d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.MISSED
                   OR (d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.ASSIGNED
                       AND d.operatingDay < :today))
            GROUP BY d.operatingDay
            ORDER BY d.operatingDay
            """)
    List<MissedDay> countMissedByOperatingDay(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("today") LocalDate today
    );

    /**
     * 학생의 봉사 지정 중 한 상태인 것을 최신 운영일부터 읽는다. 학생 본인 봉사 완료 내역(REQ-COM-003)에 쓴다.
     */
    List<VolunteerDuty> findAllByStudentIdAndStatusOrderByOperatingDayDescIdDesc(Long studentId, DutyStatus status);

    /** 그 운영일에 지정된 모든 학생. 봉사 관리 명단의 지정 상태 표시에 쓴다. */
    List<VolunteerDuty> findAllByOperatingDay(LocalDate operatingDay);

    /**
     * 지정을 완료로 바꾼다. 아직 지정 상태일 때만 바꿔 완료가 두 번 반영되지 않는다.
     *
     * @return 완료로 바꿨으면 1, 이미 완료였으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE VolunteerDuty d
            SET d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.COMPLETED, d.completedAt = :completedAt
            WHERE d.id = :id AND d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.ASSIGNED
            """)
    int complete(@Param("id") Long id, @Param("completedAt") Instant completedAt);

    /**
     * 아직 완료하지 않은 지정만 지운다. 완료한 지정은 취소할 수 없다.
     *
     * @return 지웠으면 1, 이미 완료였으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM VolunteerDuty d
            WHERE d.id = :id AND d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.ASSIGNED
            """)
    int cancel(@Param("id") Long id);

    /**
     * 지정을 미이행으로 바꾼다. 완료한 지정은 바꾸지 않는다.
     *
     * @return 미이행으로 바꿨거나 이미 미이행이면 1, 완료였으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE VolunteerDuty d
            SET d.status = com.checkup.checkup.domain.volunteer.entity.DutyStatus.MISSED
            WHERE d.id = :id AND d.status <> com.checkup.checkup.domain.volunteer.entity.DutyStatus.COMPLETED
            """)
    int markMissed(@Param("id") Long id);

    /** 운영일별 미이행 인원 조회 결과. */
    interface MissedDay {
        LocalDate getOperatingDay();

        long getMissedCount();
    }
}
