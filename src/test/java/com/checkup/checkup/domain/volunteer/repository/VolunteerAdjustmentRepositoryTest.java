package com.checkup.checkup.domain.volunteer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 봉사 조정 기록의 재시도 키 중복 무시, 마지막 조정 시각 조회,
 * 봉사 횟수 증가와 0 하한 차감을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VolunteerAdjustmentRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private VolunteerAdjustmentRepository adjustmentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long studentId;
    private Long otherStudentId;

    @BeforeEach
    void setUp() {
        studentId = saveStudent(91_001).getId();
        otherStudentId = saveStudent(91_002).getId();
    }

    @Test
    @DisplayName("같은 재시도 키는 한 번만 기록하고, 키가 없으면 매번 기록한다")
    void sameKeyIsRecordedOnce() {
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, "key-1", T0)).isEqualTo(1);
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, "key-1", T0.plusSeconds(1))).isZero();
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, null, T0)).isEqualTo(1);
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, null, T0)).isEqualTo(1);
    }

    @Test
    @DisplayName("학생별 마지막 조정 시각을 읽고, 조정하지 않은 학생은 결과에 없다")
    void lastActivityIsLatestPerStudent() {
        adjustmentRepository.insertIfAbsent(studentId, 1, null, T0);
        adjustmentRepository.insertIfAbsent(studentId, -1, null, T0.plusSeconds(60));

        Map<Long, Instant> last = adjustmentRepository.findLastActivities().stream()
                .collect(Collectors.toMap(VolunteerAdjustmentRepository.LastActivity::getStudentId,
                        VolunteerAdjustmentRepository.LastActivity::getLastActivityAt));

        assertThat(last).containsEntry(studentId, T0.plusSeconds(60)).doesNotContainKey(otherStudentId);
        assertThat(adjustmentRepository.findLastActivityAt(studentId)).isEqualTo(T0.plusSeconds(60));
        assertThat(adjustmentRepository.findLastActivityAt(otherStudentId)).isNull();
    }

    @Test
    @DisplayName("봉사 횟수는 늘리고 줄일 수 있지만 0 아래로는 줄지 않는다")
    void countNeverGoesBelowZero() {
        assertThat(studentRepository.increaseVolunteerCount(studentId)).isEqualTo(1);
        assertThat(studentRepository.decreaseVolunteerCount(studentId)).isEqualTo(1);
        assertThat(studentRepository.decreaseVolunteerCount(studentId)).isZero();

        assertThat(studentRepository.findById(studentId).orElseThrow().getVolunteerCount()).isZero();
    }

    private Student saveStudent(int studentNumber) {
        Long dataGsmId = (long) studentNumber;
        Member member = memberRepository.save(Member.create(dataGsmId, "학생", MemberRole.STUDENT));
        return studentRepository.saveAndFlush(Student.create(member, dataGsmId, 1, 1, 1, studentNumber, 301));
    }
}
