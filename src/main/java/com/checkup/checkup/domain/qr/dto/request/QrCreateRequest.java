package com.checkup.checkup.domain.qr.dto.request;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

import jakarta.validation.constraints.NotNull;

/**
 * QR 세션 생성 요청.
 *
 * @param purpose 출석 용도
 */
public record QrCreateRequest(
        @NotNull AttendancePurpose purpose
) {
}
