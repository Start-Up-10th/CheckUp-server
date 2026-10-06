package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버의 프레임 인식 응답. Spring이 후보·출석 규칙으로 다시 검증한 뒤 화면 응답으로 바꾼다. */
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
