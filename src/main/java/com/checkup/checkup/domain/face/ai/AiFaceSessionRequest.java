package com.checkup.checkup.domain.face.ai;

import java.util.List;

/** AI 서버에 인식 세션을 만들 때 보내는 모델 정보와 후보 학생 벡터. 로그에 남기지 않는다. */
public record AiFaceSessionRequest(AiFaceModel model, List<AiFaceCandidate> candidates) {
}
