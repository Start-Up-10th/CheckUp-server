package com.checkup.checkup.domain.member.repository;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.Student;
import java.time.Instant;
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

    /**
     * DataGSM 학생 id로 저장된 학생이 없을 때만 로그인 계정 없이 새로 저장한다.
     * 확인과 저장이 한 문장이라, 첫 로그인과 동기화가 같은 학생을 동시에 저장해도 unique 충돌로 실패하지 않는다.
     * 다른 트랜잭션이 같은 학생을 저장하는 중이면 그 트랜잭션이 끝날 때까지 기다린 뒤 건너뛴다.
     * 저장한 학생은 {@link #findByDatagsmStudentId}로 다시 조회해 쓴다.
     *
     * @param syncedAt 동기화 기준 시각. 로그인으로 저장하면 {@code null}이다.
     * @return 새로 저장하면 1, 이미 있으면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO student (datagsm_student_id, name, grade, class_number, number, student_number,
                                 dormitory_room, datagsm_synced_at)
            VALUES (:datagsmStudentId, :name, :grade, :classNumber, :number, :studentNumber,
                    CAST(:dormitoryRoom AS INTEGER), CAST(:syncedAt AS TIMESTAMPTZ))
            ON CONFLICT (datagsm_student_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("datagsmStudentId") Long datagsmStudentId,
            @Param("name") String name,
            @Param("grade") Integer grade,
            @Param("classNumber") Integer classNumber,
            @Param("number") Integer number,
            @Param("studentNumber") Integer studentNumber,
            @Param("dormitoryRoom") Integer dormitoryRoom,
            @Param("syncedAt") Instant syncedAt);

    /**
     * 주어진 학생 id 중 호실이 배정된 학생 수를 쿼리 한 번으로 센다. 얼굴 인식 프레임마다 후보 확인에 쓴다.
     * 저장되지 않은 id와 호실이 없는 학생은 세지 않으므로, 결과가 id 수보다 작으면 그런 후보가 있다는 뜻이다.
     *
     * @param ids 학생 id 목록. 비어 있으면 0이다.
     * @return 호실이 있는 학생 수
     */
    long countByIdInAndDormitoryRoomIsNotNull(Collection<Long> ids);

    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(Integer dormitoryRoom);

    @EntityGraph(attributePaths = "member")
    List<Student> findAllByDormitoryRoomAndDatagsmStudentIdIsNotNullOrderByNameAscStudentNumberAscIdAsc(
            Integer dormitoryRoom);

    /**
     * DataGSM 학생 ID로 학생과 회원을 한 번에 조회한다. 일괄 졸업처럼 여러 학생이 한 이벤트로 올 때 쓴다.
     *
     * @param datagsmStudentIds DataGSM 학생 ID 목록
     * @return 저장된 학생만 반환한다. 로그인 계정이 없는 학생은 {@code member}가 null이다.
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
     * 학생 행을 잠그고 봉사 횟수를 읽는다. 여러 회 조정에서 남은 횟수를 보고 실제로 뺄 횟수를 정할 때 쓴다.
     * 트랜잭션이 끝날 때까지 같은 학생의 다른 조정은 기다린다.
     *
     * @return 봉사 횟수. 학생이 없으면 비어 있다.
     */
    @Query(value = "SELECT volunteer_count FROM student WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockVolunteerCount(@Param("id") Long id);

    /**
     * 봉사 횟수를 {@code delta}만큼 바꾼다. 0 미만이 되지 않는지는 {@link #lockVolunteerCount}로 잠근 뒤 호출하는 쪽에서 확인한다.
     *
     * @return 바뀐 행 수(학생이 있으면 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Student s SET s.volunteerCount = s.volunteerCount + :delta WHERE s.id = :id")
    int changeVolunteerCount(@Param("id") Long id, @Param("delta") int delta);

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
