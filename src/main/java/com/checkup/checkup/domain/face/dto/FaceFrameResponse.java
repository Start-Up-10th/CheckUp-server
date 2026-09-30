package com.checkup.checkup.domain.face.dto;

import java.util.List;

public record FaceFrameResponse(String frameId, List<Face> faces) {
    public record Face(
            String trackId,
            List<Double> bbox,
            List<List<Double>> landmarks,
            Quality quality,
            Recognition recognition,
            int attempts,
            boolean qrRecommended
    ) {
    }

    public record Quality(double brightness, double sharpness, List<String> issues) {
    }

    public record Recognition(String status, String studentId, Double score, Double margin, String attendance) {
    }
}
