package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiFaceFrameResponse(
        String frameId,
        List<FaceResult> faces
) {
    public record FaceResult(
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

    public record Recognition(
            String status,
            @JsonProperty("studentId") String studentId,
            Double score,
            Double margin
    ) {
    }
}
