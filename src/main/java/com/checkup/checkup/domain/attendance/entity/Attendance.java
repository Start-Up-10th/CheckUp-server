package com.checkup.checkup.domain.attendance.entity;

import java.time.Instant;
import java.time.LocalDate;

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

/**
 * 학생·용도·운영일별 현재 출석 상태. 한 조합에 한 행만 있다(REQ-ATT-002).
 *
 * 현재 상태({@code attended})와 최초 유효 인증 시각({@code firstVerifiedAt})을 따로 둔다(REQ-ATT-006).
 * 자동 인증 저장은 {@link com.checkup.checkup.domain.attendance.repository.AttendanceRepository}의 원자적 쿼리로 한다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "attendance")
@Entity
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AttendancePurpose purpose;

    @Column(nullable = false)
    private LocalDate operatingDay;

    @Column(nullable = false)
    private boolean attended;

    private Instant firstVerifiedAt;

    @Enumerated(EnumType.STRING)
    private AttendanceMethod method;

    private Instant manualUpdatedAt;
}
