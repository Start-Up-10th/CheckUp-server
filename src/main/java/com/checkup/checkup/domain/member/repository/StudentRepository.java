package com.checkup.checkup.domain.member.repository;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * DataGSM 학생 id가 있는 모든 학생을 회원 정보와 함께 조회한다. 봉사 관리 명단에 쓴다.
     * 명단의 조정 API 경로가 DataGSM 학생 id라서 그 값이 없는 학생은 뺀다. 정렬은 쓰는 쪽에서 한다.
     */
    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDatagsmStudentIdIsNotNull();

    /**
     * 봉사 횟수를 1 늘린다. DB에서 바로 더해 동시에 눌러도 빠지지 않는다.
     *
     * @return 바뀐 행 수(학생이 있으면 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Student s SET s.volunteerCount = s.volunteerCount + 1 WHERE s.id = :id")
    int increaseVolunteerCount(@Param("id") Long id);

    /**
     * 봉사 횟수를 1 줄인다. 0이면 줄이지 않는다. 확인과 차감이 한 쿼리라 동시에 눌러도 0 미만이 되지 않는다.
     *
     * @return 줄였으면 1, 이미 0이라 그대로면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Student s SET s.volunteerCount = s.volunteerCount - 1 WHERE s.id = :id AND s.volunteerCount > 0")
    int decreaseVolunteerCount(@Param("id") Long id);
}
