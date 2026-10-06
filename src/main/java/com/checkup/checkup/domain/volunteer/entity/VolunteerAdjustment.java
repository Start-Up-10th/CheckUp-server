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

    /** 실제로 바뀐 횟수. 양수는 봉사 추가, 음수는 봉사 완료·감면이다. 차감은 남은 횟수까지만 하므로 요청보다 작을 수 있다. */
    @Column(nullable = false)
    private short delta;

    /** 관리자가 요청한 횟수(-99~99, 0 제외). 같은 재시도 키가 같은 요청인지 확인할 때 쓴다. */
    @Column(nullable = false)
    private short requestedDelta;

    /** 조정 종류. 관리자 조정인지 당일 봉사 완료의 차감인지 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VolunteerAdjustmentKind kind;

    /** 관리자가 남긴 사유. 남기지 않았으면 {@code null}이다. 로그에 남기지 않는다. */
    @Column(length = 100)
    private String reason;

    /** 웹이 보낸 재시도 방지 키. 보내지 않았으면 {@code null}이다. */
    @Column(length = 100)
    private String requestKey;

    @Column(nullable = false)
    private Instant createdAt;
}
