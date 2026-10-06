package com.checkup.checkup.domain.face.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.entity.FaceRecognitionLog;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Limit;

/**
 * 실제 PostgreSQL에서 최근 인식 기록이 세션·얼굴·결과마다 한 줄만 남고, 관리자·운영일·용도로 최신순 조회되며,
 * 지난 운영일 기록만 지워지는지 검증한다(#143).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FaceRecognitionLogRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant T0 = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID OTHER_SESSION = UUID.randomUUID();

    @Autowired
    private FaceRecognitionLogRepository repository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentRepository studentRepository;

    private Long adminId;
    private Long otherAdminId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        adminId = memberRepository.save(Member.create(93_001L, "관리자", MemberRole.ADMIN)).getId();
        otherAdminId = memberRepository.save(Member.create(93_002L, "다른 관리자", MemberRole.ADMIN)).getId();
        Member member = memberRepository.save(Member.create(93_003L, "학생", MemberRole.STUDENT));
        studentId = studentRepository.save(Student.create(member, 93_003L, "학생", 2, 1, 5, 2105, 301)).getId();
    }

    @Test
    @DisplayName("같은 세션·얼굴·결과는 한 줄만 남고, 결과가 다르면 따로 남는다")
    void sameTrackAndResultIsRecordedOnce() {
        assertThat(insert(SESSION, adminId, "DORMITORY", TODAY, "track-1", "FAILED", null, T0)).isEqualTo(1);
        assertThat(insert(SESSION, adminId, "DORMITORY", TODAY, "track-1", "FAILED", null, T0.plusSeconds(1))).isZero();
        assertThat(insert(SESSION, adminId, "DORMITORY", TODAY, "track-1", "SUCCESS", studentId, T0.plusSeconds(2)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("관리자·운영일·용도가 같은 기록만 최신순으로 개수만큼 읽고 학생 정보를 함께 가져온다")
    void recentLogsAreFilteredAndOrdered() {
        insert(SESSION, adminId, "DORMITORY", TODAY, "track-1", "SUCCESS", studentId, T0);
        insert(SESSION, adminId, "DORMITORY", TODAY, "track-2", "FAILED", null, T0.plusSeconds(60));
        insert(SESSION, adminId, "DORMITORY", TODAY, "track-3", "FAILED", null, T0.plusSeconds(120));
        insert(SESSION, adminId, "STUDY_ROOM", TODAY, "track-4", "FAILED", null, T0.plusSeconds(180));
        insert(OTHER_SESSION, otherAdminId, "DORMITORY", TODAY, "track-5", "FAILED", null, T0.plusSeconds(240));
        insert(SESSION, adminId, "DORMITORY", TODAY.minusDays(1), "track-6", "FAILED", null, T0.minusSeconds(86_400));

        List<FaceRecognitionLog> all = repository.findByAdminMemberIdAndOperatingDayAndPurposeOrderByRecognizedAtDescIdDesc(
                adminId, TODAY, AttendancePurpose.DORMITORY, Limit.of(10));
        List<FaceRecognitionLog> limited = repository.findByAdminMemberIdAndOperatingDayAndPurposeOrderByRecognizedAtDescIdDesc(
                adminId, TODAY, AttendancePurpose.DORMITORY, Limit.of(2));

        assertThat(all).extracting(FaceRecognitionLog::getTrackId).containsExactly("track-3", "track-2", "track-1");
        assertThat(all.get(2).getResult()).isEqualTo(FaceRecognitionResult.SUCCESS);
        assertThat(all.get(2).getStudent().getName()).isEqualTo("학생");
        assertThat(all.get(0).getStudent()).isNull();
        assertThat(limited).extracting(FaceRecognitionLog::getTrackId).containsExactly("track-3", "track-2");
    }

    @Test
    @DisplayName("정리는 기준 운영일보다 앞선 기록만 지운다")
    void deleteRemovesOnlyPreviousDays() {
        insert(SESSION, adminId, "DORMITORY", TODAY.minusDays(1), "old", "FAILED", null, T0.minusSeconds(86_400));
        insert(SESSION, adminId, "DORMITORY", TODAY, "today", "FAILED", null, T0);

        assertThat(repository.deleteByOperatingDayBefore(TODAY)).isEqualTo(1);
        assertThat(repository.findAll()).extracting(FaceRecognitionLog::getTrackId).containsExactly("today");
    }

    private int insert(UUID session, Long admin, String purpose, LocalDate day, String trackId, String result,
                       Long student, Instant at) {
        return repository.insertIfAbsent(session, admin, purpose, day, trackId, result, student, at);
    }
}
