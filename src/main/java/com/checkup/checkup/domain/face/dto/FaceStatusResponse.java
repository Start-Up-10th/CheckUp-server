package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인한 학생의 얼굴 등록 상태. 필수 동의·등록 대상 여부·등록 여부를 함께 준다. */
public record FaceStatusResponse(
        @Schema(description = "REGISTERED(등록됨) 또는 NOT_REGISTERED(미등록)") String status,
        @Schema(description = "필수 동의(개인정보, 얼굴 정보)를 마쳤는지") boolean consented,
        @Schema(description = "얼굴 등록 대상인지. DataGSM 학생 id와 호실 배정이 있어야 한다") boolean eligible,
        @Schema(description = "얼굴을 이미 등록했는지") boolean enrolled
) {
}
