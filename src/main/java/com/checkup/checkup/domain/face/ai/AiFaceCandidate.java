package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiFaceCandidate(
        @JsonProperty("student_id") String studentId,
        List<List<Double>> vectors
) {
}
