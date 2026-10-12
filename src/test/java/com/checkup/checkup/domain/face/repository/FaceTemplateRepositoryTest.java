package com.checkup.checkup.domain.face.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.face.entity.FaceTemplate;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실제 PostgreSQL에서 얼굴 인식 세션 후보 쿼리가 출석 대상 학생의 템플릿만 읽는지 검증한다(#236).
 * 조건은 {@link Student#isAttendanceEligible()}와 DataGSM 학생 id가 있는 것이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FaceTemplateRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-13T03:00:00Z");
    private static final AiFaceModel MODEL = new AiFaceModel("model-a", "v1", 256, "l2");

    @Autowired
    private FaceTemplateRepository repository;

    @Autowired
    private StudentRepository studentRepository;

    private Long eligibleId;
    private Long otherEligibleId;
    private Long noConsentId;
    private Long noFaceConsentId;
    private Long noPrivacyConsentId;
    private Long noRoomId;
    private Long noDatagsmIdId;

    @BeforeEach
    void setUp() {
        eligibleId = saveWithTemplate(eligible(94_001L, 301));
        otherEligibleId = saveWithTemplate(eligible(94_002L, 302));

        noConsentId = saveWithTemplate(Student.createWithoutMember(94_003L, "동의 안 함", 2, 1, 3, 2103, 303));

        Student noFaceConsent = eligible(94_004L, 304);
        ReflectionTestUtils.setField(noFaceConsent, "faceAgreedAt", null);
        noFaceConsentId = saveWithTemplate(noFaceConsent);

        Student noPrivacyConsent = eligible(94_005L, 305);
        ReflectionTestUtils.setField(noPrivacyConsent, "privacyAgreedAt", null);
        noPrivacyConsentId = saveWithTemplate(noPrivacyConsent);

        Student noRoom = eligible(94_006L, 306);
        noRoom.leaveDormitory();
        noRoomId = saveWithTemplate(noRoom);

        noDatagsmIdId = saveWithTemplate(eligible(null, 307));
    }

    @Test
    @DisplayName("동의 2종·호실·DataGSM 학생 id가 모두 있는 학생의 템플릿만 학생 id 순으로 읽는다")
    void findAllEligibleReturnsOnlyAttendanceEligibleStudents() {
        List<FaceTemplate> templates = repository.findAllEligible();

        assertThat(templates).extracting(template -> template.getStudent().getId())
                .containsExactly(eligibleId, otherEligibleId);
    }

    @Test
    @DisplayName("주어진 학생 중에서도 출석 대상이 아닌 학생의 템플릿은 빠진다")
    void findAllEligibleByStudentIdInSkipsIneligibleStudents() {
        List<Long> requested = List.of(eligibleId, noConsentId, noFaceConsentId, noPrivacyConsentId,
                noRoomId, noDatagsmIdId);

        List<FaceTemplate> templates = repository.findAllEligibleByStudentIdIn(requested);

        assertThat(templates).extracting(template -> template.getStudent().getId())
                .containsExactly(eligibleId);
    }

    @Test
    @DisplayName("쿼리 결과는 Student.isAttendanceEligible()과 같은 판단이다")
    void queryMatchesEntityRule() {
        List<FaceTemplate> templates = repository.findAllEligible();

        assertThat(templates).allSatisfy(template -> {
            assertThat(template.getStudent().isAttendanceEligible()).isTrue();
            assertThat(template.getStudent().getDatagsmStudentId()).isPositive();
        });
        assertThat(repository.findAll()).filteredOn(template -> template.getStudent().isAttendanceEligible()
                        && template.getStudent().getDatagsmStudentId() != null
                        && template.getStudent().getDatagsmStudentId() > 0)
                .hasSameSizeAs(templates);
    }

    private Student eligible(Long datagsmStudentId, int room) {
        Student student = Student.createWithoutMember(datagsmStudentId, "학생" + room, 2, 1, room % 100, 2100 + room % 100,
                room);
        student.agree(false, T0, "v1");
        return student;
    }

    private Long saveWithTemplate(Student student) {
        Student saved = studentRepository.save(student);
        repository.save(FaceTemplate.create(saved, MODEL, "[[1.0]]"));
        return saved.getId();
    }
}
