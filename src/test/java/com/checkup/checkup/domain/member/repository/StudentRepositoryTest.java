package com.checkup.checkup.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 학생 조회 쿼리의 정렬·필터와 회원 정보 함께 조회를 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("같은 호실 학생을 이름과 학번순으로 조회하고 회원 정보를 함께 가져온다")
    void findsRoomStudentsInNameAndNumberOrderWithMember() {
        saveStudent("홍길동", 1102, 301);
        saveStudent("김학생", 1103, 301);
        saveStudent("홍길동", 1101, 301);
        saveStudent("다른 방", 1201, 401);

        List<Student> students = studentRepository
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(301);

        assertThat(students).extracting(student -> student.getMember().getName())
                .containsExactly("김학생", "홍길동", "홍길동");
        assertThat(students).extracting(Student::getStudentNumber).containsExactly(1103, 1101, 1102);
        assertThat(students).allSatisfy(student -> assertThat(entityManagerFactory.getPersistenceUnitUtil()
                .isLoaded(student, "member")).isTrue());
    }

    @Test
    @DisplayName("호실이 미배정된 학생은 호실 조회 결과에 포함되지 않는다")
    void studentWithoutRoomIsNotInRoomSearch() {
        saveStudent("호실 없음", 1101, null);

        assertThat(studentRepository
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(301)).isEmpty();
    }

    @Test
    @DisplayName("DataGSM 학생 id로 학생을 조회하고 봉사 횟수는 0으로 저장된다")
    void findsByDatagsmStudentIdWithZeroVolunteerCount() {
        saveStudent("홍길동", 1101, 301);
        studentRepository.flush();

        assertThat(studentRepository.findByDatagsmStudentId(1101L))
                .hasValueSatisfying(student -> assertThat(student.getVolunteerCount()).isZero());
        assertThat(studentRepository.findByDatagsmStudentId(9999L)).isEmpty();
    }

    @Test
    @DisplayName("DataGSM 학생 id가 있는 학생만 회원 정보와 함께 조회한다")
    void findsOnlyStudentsWithDatagsmStudentId() {
        saveStudent("홍길동", 1102, 301);
        saveStudent("김학생", 1103, null);
        Member noId = memberRepository.save(Member.create(88_888L, "아이디 없음", MemberRole.STUDENT));
        studentRepository.saveAndFlush(Student.create(noId, null, 1, 1, 1, 1104, 301));

        List<Student> students = studentRepository.findAllByDatagsmStudentIdIsNotNull();

        assertThat(students).extracting(Student::getStudentNumber).contains(1102, 1103).doesNotContain(1104);
        assertThat(students).allSatisfy(student -> assertThat(entityManagerFactory.getPersistenceUnitUtil()
                .isLoaded(student, "member")).isTrue());
    }

    private void saveStudent(String name, int studentNumber, Integer dormitoryRoom) {
        Long dataGsmId = Long.valueOf(studentNumber);
        Member member = memberRepository.save(Member.create(dataGsmId, name, MemberRole.STUDENT));
        studentRepository.save(Student.create(member, dataGsmId, 1, 1, 1, studentNumber, dormitoryRoom));
    }
}
