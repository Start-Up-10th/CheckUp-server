package com.checkup.checkup.domain.volunteer.entity;

import com.checkup.checkup.domain.member.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 학생 한 명의 당일 봉사자 지정(REQ-COM-006, DEC-020). 학생·운영일마다 하나다.
 * 지정·완료는 {@link com.checkup.checkup.domain.volunteer.repository.VolunteerDutyRepository}의 원자적 쿼리로 한다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "volunteer_duty")
@Entity
public class VolunteerDuty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDate operatingDay;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DutyStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    /** 봉사 완료를 확인한 시각. 완료 전이면 {@code null}이다. */
    private Instant completedAt;
}
