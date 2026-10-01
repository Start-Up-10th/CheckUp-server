package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFaceModel(
        @JsonProperty("model_id") String modelId,
        String version,
        int dimension,
        String normalization
) {
}
