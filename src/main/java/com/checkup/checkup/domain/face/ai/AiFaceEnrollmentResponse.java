package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiFaceEnrollmentResponse(
        AiFaceModel model,
        @JsonProperty("reviewedFrames") int reviewedFrames,
        @JsonProperty("acceptedFrames") int acceptedFrames,
        List<List<Double>> vectors
) {
}
