package com.checkup.checkup.domain.member.repository;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByMember(Member member);

    Optional<Student> findByMemberId(Long memberId);

    @EntityGraph(attributePaths = "member")
    Optional<Student> findByDatagsmStudentId(Long datagsmStudentId);

    boolean existsByIdAndDormitoryRoomIsNotNull(Long id);

    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(Integer dormitoryRoom);

    /**
     * DataGSM 학생 ID로 학생과 회원을 한 번에 조회한다. 일괄 졸업처럼 여러 학생이 한 이벤트로 올 때 쓴다.
     *
     * @param datagsmStudentIds DataGSM 학생 ID 목록
     * @return 저장된 학생만 반환한다. 로그인한 적 없는 학생은 포함되지 않는다.
     */
    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDatagsmStudentIdIn(Collection<Long> datagsmStudentIds);
}
