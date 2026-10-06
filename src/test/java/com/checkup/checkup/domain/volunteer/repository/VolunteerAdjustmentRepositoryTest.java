package com.checkup.checkup.domain.volunteer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustmentKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Limit;

/**
 * 실제 PostgreSQL에서 봉사 조정 기록의 재시도 키 중복 무시, 마지막 조정 시각 조회,
 * 봉사 횟수 증가와 0 하한 차감, 사유·요청 횟수 기록과 잠금 조회·여러 회 변경(#139), 학생별 최신순 이력 조회(#140), 조정 종류 기록(#176)을 검증한다.
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
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", "key-1", T0)).isEqualTo(1);
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", "key-1", T0.plusSeconds(1))).isZero();
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", null, T0)).isEqualTo(1);
        assertThat(adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", null, T0)).isEqualTo(1);
    }

    @Test
    @DisplayName("학생별 마지막 조정 시각을 읽고, 조정하지 않은 학생은 결과에 없다")
    void lastActivityIsLatestPerStudent() {
        adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", null, T0);
        adjustmentRepository.insertIfAbsent(studentId, -1, -1, null, "ADMIN", null, T0.plusSeconds(60));

        Map<Long, Instant> last = adjustmentRepository.findLastActivities().stream()
                .collect(Collectors.toMap(VolunteerAdjustmentRepository.LastActivity::getStudentId,
                        VolunteerAdjustmentRepository.LastActivity::getLastActivityAt));

        assertThat(last).containsEntry(studentId, T0.plusSeconds(60)).doesNotContainKey(otherStudentId);
        assertThat(adjustmentRepository.findLastActivityAt(studentId)).isEqualTo(T0.plusSeconds(60));
        assertThat(adjustmentRepository.findLastActivityAt(otherStudentId)).isNull();
    }

    @Test
    @DisplayName("실제 바뀐 횟수·요청 횟수·사유를 함께 기록한다")
    void reasonAndRequestedDeltaAreRecorded() {
        adjustmentRepository.insertIfAbsent(studentId, -2, -5, "감면", "ADMIN", "key-2", T0);

        assertThat(adjustmentRepository.findByRequestKey("key-2")).hasValueSatisfying(adjustment -> {
            assertThat(adjustment.getDelta()).isEqualTo((short) -2);
            assertThat(adjustment.getRequestedDelta()).isEqualTo((short) -5);
            assertThat(adjustment.getReason()).isEqualTo("감면");
        });
    }

    @Test
    @DisplayName("조정 종류를 함께 기록해 관리자 조정과 당일 봉사 완료를 구분한다")
    void kindIsRecorded() {
        adjustmentRepository.insertIfAbsent(studentId, 1, 1, null, "ADMIN", "key-admin", T0);
        adjustmentRepository.insertIfAbsent(studentId, -1, -1, null, "DUTY_COMPLETION", "key-duty", T0);

        assertThat(adjustmentRepository.findByRequestKey("key-admin"))
                .hasValueSatisfying(adjustment -> assertThat(adjustment.getKind()).isEqualTo(VolunteerAdjustmentKind.ADMIN));
        assertThat(adjustmentRepository.findByRequestKey("key-duty"))
                .hasValueSatisfying(adjustment -> assertThat(adjustment.getKind())
                        .isEqualTo(VolunteerAdjustmentKind.DUTY_COMPLETION));
    }

    @Test
    @DisplayName("잠금 조회로 봉사 횟수를 읽고 여러 회 변경을 반영한다")
    void lockAndChangeVolunteerCount() {
        assertThat(studentRepository.lockVolunteerCount(studentId)).contains(0);
        assertThat(studentRepository.changeVolunteerCount(studentId, 7)).isEqualTo(1);
        assertThat(studentRepository.changeVolunteerCount(studentId, -3)).isEqualTo(1);

        assertThat(studentRepository.lockVolunteerCount(studentId)).contains(4);
        assertThat(studentRepository.lockVolunteerCount(-1L)).isEmpty();
    }

    @Test
    @DisplayName("학생별 조정 기록을 최신순으로 개수만큼 읽고 다른 학생 기록은 빼며, 같은 시각이면 나중 기록이 앞이다")
    void adjustmentsAreReadNewestFirstPerStudent() {
        adjustmentRepository.insertIfAbsent(studentId, 1, 1, "첫째", "ADMIN", null, T0);
        adjustmentRepository.insertIfAbsent(studentId, 2, 2, "둘째", "ADMIN", null, T0.plusSeconds(60));
        adjustmentRepository.insertIfAbsent(studentId, -1, -1, "셋째", "ADMIN", null, T0.plusSeconds(60));
        adjustmentRepository.insertIfAbsent(otherStudentId, 3, 3, "다른 학생", "ADMIN", null, T0.plusSeconds(120));

        List<VolunteerAdjustment> all = adjustmentRepository
                .findByStudent_IdOrderByCreatedAtDescIdDesc(studentId, Limit.of(10));
        List<VolunteerAdjustment> limited = adjustmentRepository
                .findByStudent_IdOrderByCreatedAtDescIdDesc(studentId, Limit.of(2));

        assertThat(all).extracting(VolunteerAdjustment::getReason).containsExactly("셋째", "둘째", "첫째");
        assertThat(limited).extracting(VolunteerAdjustment::getReason).containsExactly("셋째", "둘째");
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
        return studentRepository.saveAndFlush(Student.create(member, dataGsmId, "학생", 1, 1, 1, studentNumber, 301));
    }
}
