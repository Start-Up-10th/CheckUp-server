package com.checkup.checkup.domain.volunteer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 당일 봉사자 지정이 학생·운영일마다 하나이고,
 * 완료는 한 번만 반영되며 완료한 지정은 취소되지 않는지, 학생의 완료 내역을 최신 운영일부터 읽는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VolunteerDutyRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final Instant T0 = Instant.parse("2026-10-01T03:00:00Z");

    @Autowired
    private VolunteerDutyRepository dutyRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long studentId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.create(92_001L, "학생", MemberRole.STUDENT));
        studentId = studentRepository.saveAndFlush(Student.create(member, 92_001L, 1, 1, 1, 92_001, 301)).getId();
    }

    @Test
    @DisplayName("같은 학생은 같은 운영일에 한 번만 지정되고 다른 날은 따로 지정된다")
    void assignOncePerDay() {
        assertThat(dutyRepository.assign(studentId, DAY, T0)).isEqualTo(1);
        assertThat(dutyRepository.assign(studentId, DAY, T0.plusSeconds(1))).isZero();
        assertThat(dutyRepository.assign(studentId, DAY.plusDays(1), T0)).isEqualTo(1);

        assertThat(dutyRepository.findAllByOperatingDay(DAY)).hasSize(1);
        assertThat(dutyRepository.findByStudentIdAndOperatingDay(studentId, DAY))
                .get().extracting(VolunteerDuty::getStatus).isEqualTo(DutyStatus.ASSIGNED);
    }

    @Test
    @DisplayName("완료는 한 번만 반영되고 완료한 지정은 취소되지 않는다")
    void completeOnceAndCannotCancelCompleted() {
        dutyRepository.assign(studentId, DAY, T0);
        Long id = dutyRepository.findByStudentIdAndOperatingDay(studentId, DAY).orElseThrow().getId();

        assertThat(dutyRepository.complete(id, T0.plusSeconds(60))).isEqualTo(1);
        assertThat(dutyRepository.complete(id, T0.plusSeconds(120))).isZero();
        assertThat(dutyRepository.cancel(id)).isZero();

        VolunteerDuty duty = dutyRepository.findById(id).orElseThrow();
        assertThat(duty.getStatus()).isEqualTo(DutyStatus.COMPLETED);
        assertThat(duty.getCompletedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    @DisplayName("학생의 완료한 봉사만 최신 운영일부터 읽는다")
    void findCompletedNewestFirst() {
        Member other = memberRepository.save(Member.create(92_002L, "다른학생", MemberRole.STUDENT));
        Long otherId = studentRepository.saveAndFlush(Student.create(other, 92_002L, 1, 1, 2, 92_002, 301)).getId();
        dutyRepository.assign(studentId, DAY, T0);
        dutyRepository.assign(studentId, DAY.plusDays(2), T0);
        dutyRepository.assign(studentId, DAY.plusDays(5), T0);
        dutyRepository.assign(otherId, DAY.plusDays(3), T0);
        complete(studentId, DAY);
        complete(studentId, DAY.plusDays(2));
        complete(otherId, DAY.plusDays(3));

        assertThat(dutyRepository.findAllByStudentIdAndStatusOrderByOperatingDayDescIdDesc(studentId, DutyStatus.COMPLETED))
                .extracting(VolunteerDuty::getOperatingDay)
                .containsExactly(DAY.plusDays(2), DAY);
    }

    private void complete(Long student, LocalDate day) {
        Long id = dutyRepository.findByStudentIdAndOperatingDay(student, day).orElseThrow().getId();
        dutyRepository.complete(id, T0.plusSeconds(60));
    }

    @Test
    @DisplayName("완료 전 지정은 취소하면 지워진다")
    void cancelAssigned() {
        dutyRepository.assign(studentId, DAY, T0);
        Long id = dutyRepository.findByStudentIdAndOperatingDay(studentId, DAY).orElseThrow().getId();

        assertThat(dutyRepository.cancel(id)).isEqualTo(1);
        assertThat(dutyRepository.findByStudentIdAndOperatingDay(studentId, DAY)).isEmpty();
    }
}
