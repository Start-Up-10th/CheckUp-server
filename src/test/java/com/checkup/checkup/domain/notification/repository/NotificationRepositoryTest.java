package com.checkup.checkup.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.notification.entity.Notification;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 알림 저장의 중복 무시, 학생별 최신순 조회, 읽음 처리, 지난 출석 알림 삭제를 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManager entityManager;

    private Long studentId;
    private Long otherStudentId;

    @BeforeEach
    void setUp() {
        studentId = saveStudent(1101).getId();
        otherStudentId = saveStudent(1102).getId();
    }

    @Test
    @DisplayName("같은 학생·유형·원본의 알림은 한 번만 저장하고 두 번째는 예외 없이 0을 돌려준다")
    void duplicateInsertIsIgnored() {
        int first = insert(studentId, NotificationType.ATTENDANCE, "STUDY_ROOM:2026-09-30", T0);
        int second = insert(studentId, NotificationType.ATTENDANCE, "STUDY_ROOM:2026-09-30", T0.plusSeconds(1));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId)).hasSize(1);
    }

    @Test
    @DisplayName("원본이나 유형이 다르면 따로 저장한다")
    void differentSourceOrTypeIsStored() {
        insert(studentId, NotificationType.ATTENDANCE, "STUDY_ROOM:2026-09-30", T0);
        insert(studentId, NotificationType.ATTENDANCE, "DORMITORY:2026-09-30", T0);
        insert(studentId, NotificationType.VOLUNTEER, "STUDY_ROOM:2026-09-30", T0);

        assertThat(notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId)).hasSize(3);
    }

    @Test
    @DisplayName("본인 알림만 최신순으로 최대 50개 조회한다")
    void listIsOwnLatestFifty() {
        for (int i = 0; i < 51; i++) {
            insert(studentId, NotificationType.ATTENDANCE, "KEY:" + i, T0.plusSeconds(i));
        }
        insert(otherStudentId, NotificationType.ATTENDANCE, "OTHER", T0.plusSeconds(100));

        List<Notification> list = notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId);

        assertThat(list).hasSize(50);
        assertThat(list.getFirst().getSourceKey()).isEqualTo("KEY:50");
        assertThat(list.getLast().getSourceKey()).isEqualTo("KEY:1");
        assertThat(list).allSatisfy(n -> assertThat(n.getSourceKey()).startsWith("KEY:"));
    }

    @Test
    @DisplayName("본인의 읽지 않은 알림만 읽음으로 바꾸고 이미 읽은 시각과 다른 학생 알림은 그대로 둔다")
    void markAllReadChangesOnlyOwnUnread() {
        insert(studentId, NotificationType.ATTENDANCE, "READ", T0);
        insert(studentId, NotificationType.ATTENDANCE, "UNREAD", T0);
        insert(otherStudentId, NotificationType.ATTENDANCE, "OTHER", T0);
        notificationRepository.markAllRead(studentId, T0.plusSeconds(10));
        insert(studentId, NotificationType.ATTENDANCE, "NEW", T0.plusSeconds(20));

        int changed = notificationRepository.markAllRead(studentId, T0.plusSeconds(30));
        entityManager.clear();

        assertThat(changed).isEqualTo(1);
        assertThat(notificationRepository.existsByStudentIdAndReadAtIsNull(studentId)).isFalse();
        assertThat(notificationRepository.existsByStudentIdAndReadAtIsNull(otherStudentId)).isTrue();
        assertThat(notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId))
                .filteredOn(n -> !n.getSourceKey().equals("NEW"))
                .extracting(Notification::getReadAt)
                .containsOnly(T0.plusSeconds(10));
    }

    @Test
    @DisplayName("기준 시각 전에 만든 해당 유형 알림만 지운다")
    void deleteByTypeBeforeDeletesOnlyOldOfType() {
        Instant boundary = T0.plusSeconds(60);
        insert(studentId, NotificationType.ATTENDANCE, "OLD", T0);
        insert(studentId, NotificationType.ATTENDANCE, "AT_BOUNDARY", boundary);
        insert(studentId, NotificationType.VOLUNTEER, "OLD_VOLUNTEER", T0);

        int deleted = notificationRepository.deleteByTypeBefore(NotificationType.ATTENDANCE, boundary);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId))
                .extracting(Notification::getSourceKey)
                .containsExactlyInAnyOrder("AT_BOUNDARY", "OLD_VOLUNTEER");
    }

    private int insert(Long studentId, NotificationType type, String sourceKey, Instant createdAt) {
        return notificationRepository.insertIfAbsent(studentId, type.name(), sourceKey, "알림", createdAt);
    }

    private Student saveStudent(int studentNumber) {
        Long dataGsmId = Long.valueOf(studentNumber);
        Member member = memberRepository.save(Member.create(dataGsmId, "학생", MemberRole.STUDENT));
        return studentRepository.saveAndFlush(Student.create(member, dataGsmId, "학생", 1, 1, 1, studentNumber, 301));
    }
}
