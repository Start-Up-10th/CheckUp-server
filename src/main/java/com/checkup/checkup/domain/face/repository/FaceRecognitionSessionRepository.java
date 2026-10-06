package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.face.entity.FaceRecognitionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 관리자 얼굴 인식 세션. 프레임 잠금과 비활성 전환은 조건부 UPDATE 한 문장으로 한다. */
public interface FaceRecognitionSessionRepository extends JpaRepository<FaceRecognitionSession, UUID> {
    Optional<FaceRecognitionSession> findByIdAndAdminMemberId(UUID id, Long adminMemberId);

    Optional<FaceRecognitionSession> findByIdAndAdminMemberIdAndActiveTrue(UUID id, Long adminMemberId);

    List<FaceRecognitionSession> findAllByAdminMemberId(Long adminMemberId);

    List<FaceRecognitionSession> findAllByIdIn(List<UUID> ids);

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.lastActivityAt = :now, " +
            "s.frameLockToken = :token, s.frameLockUntil = :lockUntil " +
            "WHERE s.id = :id AND s.adminMemberId = :adminId AND s.active = true " +
            "AND s.lastActivityAt <= :cutoff " +
            "AND (s.frameLockToken IS NULL OR s.frameLockUntil <= :now)")
    int claimFrame(
            @Param("id") UUID id,
            @Param("adminId") Long adminId,
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff,
            @Param("token") UUID token,
            @Param("lockUntil") Instant lockUntil
    );

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.frameLockUntil = :lockUntil " +
            "WHERE s.id = :id AND s.adminMemberId = :adminId AND s.active = true " +
            "AND s.frameLockToken = :token AND s.frameLockUntil > :now")
    int extendFrame(@Param("id") UUID id, @Param("adminId") Long adminId, @Param("token") UUID token,
                    @Param("now") Instant now, @Param("lockUntil") Instant lockUntil);

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.frameLockToken = null, s.frameLockUntil = null, " +
            "s.lastActivityAt = :now WHERE s.id = :id AND s.adminMemberId = :adminId " +
            "AND s.active = true AND s.frameLockToken = :token")
    int releaseFrame(
            @Param("id") UUID id,
            @Param("adminId") Long adminId,
            @Param("token") UUID token,
            @Param("now") Instant now
    );

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.active = false, " +
            "s.frameLockToken = null, s.frameLockUntil = null " +
            "WHERE s.id = :id AND s.adminMemberId = :adminId AND s.active = true")
    int markInactive(@Param("id") UUID id, @Param("adminId") Long adminId);

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.active = false, " +
            "s.frameLockToken = null, s.frameLockUntil = null " +
            "WHERE s.id = :id AND s.active = true AND s.lastActivityAt < :cutoff " +
            "AND (s.frameLockToken IS NULL OR s.frameLockUntil <= :now)")
    int markInactiveIfIdle(@Param("id") UUID id, @Param("cutoff") Instant cutoff,
                           @Param("now") Instant now);

    @Query("SELECT s FROM FaceRecognitionSession s WHERE s.active = false OR " +
            "(s.lastActivityAt < :cutoff AND (s.frameLockToken IS NULL OR s.frameLockUntil <= :now))")
    List<FaceRecognitionSession> findIdleBefore(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
