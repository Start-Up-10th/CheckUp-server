package com.checkup.checkup.domain.face.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 인식 세션 후보 학생 한 명. DataGSM 학생 id와 대표 벡터 목록이다. 로그에 남기지 않는다. */
public record AiFaceCandidate(
        @JsonProperty("student_id") String studentId,
        List<List<Double>> vectors
) {
}
