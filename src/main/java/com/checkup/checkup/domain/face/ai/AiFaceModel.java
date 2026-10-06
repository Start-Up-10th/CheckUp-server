package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 얼굴 임베딩 모델 정보. 등록 템플릿과 인식 세션의 모델이 같아야 대조할 수 있다. */
public record AiFaceModel(
        @JsonProperty("model_id") String modelId,
        String version,
        int dimension,
        String normalization
) {
}
