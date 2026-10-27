package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.face.entity.FaceTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 학생별 얼굴 등록 템플릿(대표 벡터). 학생마다 하나다. */
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {
    boolean existsByStudent_Id(Long studentId);

    Optional<FaceTemplate> findByStudent_Id(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT template FROM FaceTemplate template WHERE template.student.id = :studentId")
    Optional<FaceTemplate> findForUpdateByStudentId(@Param("studentId") Long studentId);

    void deleteByStudent_Id(Long studentId);

    /**
     * 호실 번호가 {@code firstRoom}~{@code lastRoom}인 출석 대상 학생의 템플릿만 학생 id 순으로 읽는다.
     * 얼굴 인식 세션을 만들 때 고른 층 학생만 후보로 쓰려고 부른다(#256).
     * 조건은 {@link com.checkup.checkup.domain.member.entity.Student#isAttendanceEligible()}(동의 2종, 호실)과
     * DataGSM 학생 id가 있는 것이다. 출석 대상 규칙이 바뀌면 이 쿼리와 {@link #findAllEligibleByStudentIdIn}도 같이 바꾼다.
     */
    @Query("""
        SELECT template FROM FaceTemplate template JOIN FETCH template.student student
        WHERE student.dormitoryRoom BETWEEN :firstRoom AND :lastRoom
          AND student.privacyAgreedAt IS NOT NULL AND student.faceAgreedAt IS NOT NULL
          AND student.datagsmStudentId > 0
        ORDER BY student.id
        """)
    List<FaceTemplate> findAllEligibleInRooms(@Param("firstRoom") int firstRoom, @Param("lastRoom") int lastRoom);

    /**
     * 주어진 학생 중 출석 대상인 학생의 템플릿만 읽는다. AI 세션을 다시 만들 때 쓴다.
     * 조건은 {@link #findAllEligibleInRooms}와 같다.
     */
    @Query("""
        SELECT template FROM FaceTemplate template JOIN FETCH template.student student
        WHERE student.id IN :studentIds
          AND student.privacyAgreedAt IS NOT NULL AND student.faceAgreedAt IS NOT NULL
          AND student.dormitoryRoom IS NOT NULL AND student.datagsmStudentId > 0
        """)
    List<FaceTemplate> findAllEligibleByStudentIdIn(@Param("studentIds") Collection<Long> studentIds);
}
