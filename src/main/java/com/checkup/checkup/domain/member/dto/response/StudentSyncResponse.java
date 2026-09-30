package com.checkup.checkup.domain.member.dto.response;

/**
 * 관리자가 요청한 DataGSM 학생 수동 동기화의 결과.
 *
 * @param received DataGSM에서 받은 학생 수(졸업·자퇴 포함)
 * @param synced   저장된 학생 중 실제로 정보·권한을 반영한 학생 수
 */
public record StudentSyncResponse(
        int received,
        int synced
) {}
