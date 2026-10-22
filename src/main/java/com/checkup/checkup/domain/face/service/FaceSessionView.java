package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 얼굴 인식 세션의 읽기 전용 상태와 후보 학생 DB id 목록. {@code floor}는 카메라가 맡은 층이고 층 도입 전 세션은 null이다. */
public record FaceSessionView(
        UUID id,
        Long adminMemberId,
        AttendancePurpose purpose,
        Integer floor,
        Instant lastActivityAt,
        boolean active,
        Set<Long> candidateStudentIds
) {
}
