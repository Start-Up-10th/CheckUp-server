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
    void 호실별_학생을_이름과_학번순으로_조회하고_회원_이름을_한번에_가져온다() {
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
    void 호실이_미배정된_학생은_호실_검색에_포함되지_않는다() {
        saveStudent("호실 없음", 1101, null);

        assertThat(studentRepository
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(301)).isEmpty();
    }

    private void saveStudent(String name, int studentNumber, Integer dormitoryRoom) {
        Long dataGsmId = Long.valueOf(studentNumber);
        Member member = memberRepository.save(Member.create(dataGsmId, name, MemberRole.STUDENT));
        studentRepository.save(Student.create(member, dataGsmId, 1, 1, 1, studentNumber, dormitoryRoom));
    }
}
