package com.checkup.checkup.domain.face.ai;

import java.util.List;

public record AiFaceSessionRequest(AiFaceModel model, List<AiFaceCandidate> candidates) {
}
