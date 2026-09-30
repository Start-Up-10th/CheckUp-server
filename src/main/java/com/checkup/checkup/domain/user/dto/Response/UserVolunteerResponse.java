package com.checkup.checkup.domain.user.dto.Response;

/**
 * 학생의 누적 봉사 횟수 응답.
 *
 * @param studentId DataGSM 학생 id
 * @param volunteerCount 누적 봉사 횟수
 */
public record UserVolunteerResponse(Long studentId, int volunteerCount) {
}
