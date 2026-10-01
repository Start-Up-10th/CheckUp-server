package com.checkup.checkup.domain.volunteer.repository;

import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;

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
}
