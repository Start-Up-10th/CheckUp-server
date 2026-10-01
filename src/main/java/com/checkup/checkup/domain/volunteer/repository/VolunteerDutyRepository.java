package com.checkup.checkup.domain.volunteer.repository;

import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
}
