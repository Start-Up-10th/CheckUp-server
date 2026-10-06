package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.entity.FaceRecognitionLog;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 얼굴 인식 최근 기록(#143).
 */
public interface FaceRecognitionLogRepository extends JpaRepository<FaceRecognitionLog, Long> {

    /**
     * 기록을 한 줄 남긴다. 같은 세션·같은 얼굴(trackId)·같은 결과가 이미 있으면 예외 없이 무시한다.
     * 그래서 같은 얼굴이 여러 프레임에 나와도 성공·실패가 각각 한 줄만 남는다.
     *
     * @param studentId 알아본 학생 DB id. 못 알아봤으면 {@code null}
     * @return 새로 남겼으면 1, 이미 있어 무시했으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO face_recognition_log
                (session_id, admin_member_id, purpose, operating_day, track_id, result, student_id, recognized_at)
            VALUES
                (:sessionId, :adminMemberId, :purpose, :operatingDay, :trackId, :result,
                 CAST(:studentId AS BIGINT), :recognizedAt)
            ON CONFLICT (session_id, track_id, result) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("sessionId") UUID sessionId,
            @Param("adminMemberId") Long adminMemberId,
            @Param("purpose") String purpose,
            @Param("operatingDay") LocalDate operatingDay,
            @Param("trackId") String trackId,
            @Param("result") String result,
            @Param("studentId") Long studentId,
            @Param("recognizedAt") Instant recognizedAt
    );

    /**
     * 관리자 한 명이 연 세션의 한 운영일·한 용도 기록을 학생 정보와 함께 최신순으로 읽는다.
     * 같은 시각이면 나중에 남긴 기록이 앞이다.
     */
    @EntityGraph(attributePaths = "student")
    List<FaceRecognitionLog> findByAdminMemberIdAndOperatingDayAndPurposeOrderByRecognizedAtDescIdDesc(
            Long adminMemberId, LocalDate operatingDay, AttendancePurpose purpose, Limit limit);

    /**
     * 기준 운영일보다 앞선 기록을 지운다.
     *
     * @return 지운 기록 수
     */
    @Modifying
    @Query("DELETE FROM FaceRecognitionLog l WHERE l.operatingDay < :operatingDay")
    int deleteByOperatingDayBefore(@Param("operatingDay") LocalDate operatingDay);
}
