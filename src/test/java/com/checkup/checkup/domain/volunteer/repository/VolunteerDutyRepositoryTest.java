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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 당일 봉사자 지정이 학생·운영일마다 하나이고,
 * 완료는 한 번만 반영되며 완료한 지정은 취소되지 않는지, 학생의 완료 내역을 최신 운영일부터 읽는지,
 * 미이행 처리와 날짜별 기록·월 단위 미이행 요약(#250)을 검증한다.
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
        studentId = studentRepository.saveAndFlush(Student.create(member, 92_001L, "학생", 1, 1, 1, 92_001, 301)).getId();
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
        Long otherId = studentRepository.saveAndFlush(Student.create(other, 92_002L, "다른학생", 1, 1, 2, 92_002, 301)).getId();
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

    @Test
    @DisplayName("미이행은 지정·미이행을 미이행으로 바꾸고 완료한 지정은 바꾸지 않는다")
    void markMissedSkipsCompleted() {
        Long assigned = assign(studentId, DAY);
        Long completed = assign(studentId, DAY.plusDays(1));
        dutyRepository.complete(completed, T0.plusSeconds(60));

        assertThat(dutyRepository.markMissed(assigned)).isEqualTo(1);
        assertThat(dutyRepository.markMissed(assigned)).isEqualTo(1);
        assertThat(dutyRepository.markMissed(completed)).isZero();

        assertThat(dutyRepository.findById(assigned).orElseThrow().getStatus()).isEqualTo(DutyStatus.MISSED);
        assertThat(dutyRepository.findById(completed).orElseThrow().getStatus()).isEqualTo(DutyStatus.COMPLETED);
        assertThat(dutyRepository.cancel(assigned)).isZero();
    }

    @Test
    @DisplayName("날짜별 기록은 그 운영일의 지정만 학생과 함께 읽고, 잠금 조회는 학생·운영일로 찾는다")
    void findWithStudentByDay() {
        assign(studentId, DAY);
        assign(studentId, DAY.plusDays(1));

        List<VolunteerDuty> duties = dutyRepository.findAllWithStudentByOperatingDay(DAY);

        assertThat(duties).singleElement()
                .satisfies(duty -> assertThat(duty.getStudent().getName()).isEqualTo("학생"));
        assertThat(dutyRepository.lockByStudentIdAndOperatingDay(studentId, DAY.plusDays(1))).isPresent();
        assertThat(dutyRepository.lockByStudentIdAndOperatingDay(studentId, DAY.plusDays(2))).isEmpty();
    }

    @Test
    @DisplayName("월 요약은 미이행과 오늘 전 운영일의 지정을 날짜별로 세고, 완료·오늘 지정·기간 밖은 세지 않는다")
    void countMissedByDay() {
        Member other = memberRepository.save(Member.create(92_003L, "다른학생", MemberRole.STUDENT));
        Long otherId = studentRepository.saveAndFlush(Student.create(other, 92_003L, "다른학생", 1, 1, 3, 92_003, 302)).getId();
        LocalDate today = DAY.plusDays(3);
        dutyRepository.markMissed(assign(studentId, DAY));
        assign(otherId, DAY);
        dutyRepository.complete(assign(studentId, DAY.plusDays(1)), T0);
        assign(studentId, DAY.plusDays(2));
        assign(studentId, today);
        dutyRepository.markMissed(assign(otherId, today));
        assign(studentId, DAY.minusDays(1));

        List<VolunteerDutyRepository.MissedDay> days =
                dutyRepository.countMissedByOperatingDay(DAY, DAY.plusDays(30), today);

        assertThat(days).extracting(VolunteerDutyRepository.MissedDay::getOperatingDay)
                .containsExactly(DAY, DAY.plusDays(2), today);
        assertThat(days).extracting(VolunteerDutyRepository.MissedDay::getMissedCount)
                .containsExactly(2L, 1L, 1L);
    }

    private Long assign(Long student, LocalDate day) {
        dutyRepository.assign(student, day, T0);
        return dutyRepository.findByStudentIdAndOperatingDay(student, day).orElseThrow().getId();
    }
}
