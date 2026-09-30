package com.checkup.checkup.domain.face.dto;

import java.time.Instant;

public record FaceConsentResponse(boolean consented, String version, Instant consentedAt) {
}
