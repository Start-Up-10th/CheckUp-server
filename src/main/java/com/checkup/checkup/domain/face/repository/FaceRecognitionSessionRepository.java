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

public interface FaceRecognitionSessionRepository extends JpaRepository<FaceRecognitionSession, UUID> {
    Optional<FaceRecognitionSession> findByIdAndAdminMemberId(UUID id, Long adminMemberId);

    Optional<FaceRecognitionSession> findByIdAndAdminMemberIdAndActiveTrue(UUID id, Long adminMemberId);

    List<FaceRecognitionSession> findAllByAdminMemberId(Long adminMemberId);

    List<FaceRecognitionSession> findAllByIdIn(List<UUID> ids);

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.lastActivityAt = :now " +
            "WHERE s.id = :id AND s.adminMemberId = :adminId AND s.active = true " +
            "AND s.lastActivityAt <= :cutoff")
    int claimFrame(
            @Param("id") UUID id,
            @Param("adminId") Long adminId,
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff
    );

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.active = false " +
            "WHERE s.id = :id AND s.adminMemberId = :adminId AND s.active = true")
    int markInactive(@Param("id") UUID id, @Param("adminId") Long adminId);

    @Modifying
    @Query("UPDATE FaceRecognitionSession s SET s.active = false " +
            "WHERE s.id = :id AND s.active = true AND s.lastActivityAt < :cutoff")
    int markInactiveIfIdle(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    @Query("SELECT s FROM FaceRecognitionSession s WHERE s.active = false OR s.lastActivityAt < :cutoff")
    List<FaceRecognitionSession> findIdleBefore(@Param("cutoff") Instant cutoff);
}
