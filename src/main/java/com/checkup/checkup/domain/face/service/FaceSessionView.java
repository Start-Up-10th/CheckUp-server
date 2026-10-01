package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record FaceSessionView(
        UUID id,
        Long adminMemberId,
        AttendancePurpose purpose,
        Instant lastActivityAt,
        boolean active,
        Set<Long> candidateStudentIds
) {
}
