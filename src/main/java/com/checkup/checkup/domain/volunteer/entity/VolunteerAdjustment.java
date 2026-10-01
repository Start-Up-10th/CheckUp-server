package com.checkup.checkup.domain.volunteer.entity;

import com.checkup.checkup.domain.member.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 봉사 횟수 조정 한 번의 기록(REQ-COM-002, DEC-020). 저장은
 * {@link com.checkup.checkup.domain.volunteer.repository.VolunteerAdjustmentRepository}의 쿼리로 한다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "volunteer_adjustment")
@Entity
public class VolunteerAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** +1(봉사 추가) 또는 -1(봉사 완료·감면). */
    @Column(nullable = false)
    private short delta;

    /** 웹이 보낸 재시도 방지 키. 보내지 않았으면 {@code null}이다. */
    @Column(length = 100)
    private String requestKey;

    @Column(nullable = false)
    private Instant createdAt;
}
