package com.checkup.checkup.domain.member.repository;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByMember(Member member);

    Optional<Student> findByMemberId(Long memberId);

    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(Integer dormitoryRoom);
}
