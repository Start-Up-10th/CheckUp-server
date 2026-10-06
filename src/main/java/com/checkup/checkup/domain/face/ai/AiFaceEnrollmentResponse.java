package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버가 등록 영상에서 뽑은 대표 벡터와 프레임 검사 결과. 로그에 남기지 않는다. */
public record AiFaceEnrollmentResponse(
        AiFaceModel model,
        @JsonProperty("reviewedFrames") int reviewedFrames,
        @JsonProperty("acceptedFrames") int acceptedFrames,
        List<List<Double>> vectors
) {
}
