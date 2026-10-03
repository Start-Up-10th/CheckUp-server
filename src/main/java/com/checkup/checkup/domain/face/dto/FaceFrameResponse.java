package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FaceFrameResponse(String frameId, List<Face> faces) {
    public record Face(
            String trackId,
            @Schema(description = "Normalized [x, y, width, height], each value in the 0..1 range.")
            List<Double> bbox,
            @Schema(description = "Landmark [x, y] points in source-frame pixel coordinates.")
            List<List<Double>> landmarks,
            Quality quality,
            Recognition recognition,
            int attempts,
            boolean qrRecommended
    ) {
    }

    public record Quality(double brightness, double sharpness, List<String> issues) {
    }

    public record Recognition(
            @Schema(description = "KNOWN, UNKNOWN, or NOT_ATTEMPTED.")
            String status,
            @Schema(description = "Student name; null unless status is KNOWN.")
            String studentName,
            @Schema(description = "Display student number; null unless status is KNOWN.")
            Integer studentNumber,
            @Schema(description = "RECORDED, DUPLICATE, STALE, or REJECTED; null unless status is KNOWN.")
            String attendance
    ) {
    }
}
