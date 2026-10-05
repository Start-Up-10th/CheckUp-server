package com.checkup.checkup.domain.webhook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자가 요청한 DataGSM 학생 수동 동기화의 결과.
 *
 * @param received DataGSM에서 받은 학생 수(졸업·자퇴 포함)
 * @param synced   정보·권한을 반영했거나 로그인 계정 없이 새로 저장한 학생 수
 */
public record StudentSyncResponse(
        @Schema(description = "DataGSM에서 받은 학생 수(졸업·자퇴 포함)", example = "420") int received,
        @Schema(description = "정보·권한을 반영했거나 새로 저장한 학생 수", example = "415") int synced
) {}
