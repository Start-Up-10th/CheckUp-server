package com.checkup.checkup.domain.volunteer.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 봉사 관리 명단의 학생 한 명. 명단은 저장된 전체 학생이다.
 *
 * @param studentId      DataGSM 학생 id. 횟수 조정 API의 경로 값이다.
 * @param name           이름
 * @param studentNumber  학번
 * @param dormitoryRoom  호실. 미배정이면 {@code null}
 * @param volunteerCount 앞으로 해야 할 봉사 횟수
 * @param lastActivityAt 마지막 횟수 조정 시각. 조정한 적 없으면 {@code null}(화면은 {@code -})
 * @param todayDuty      오늘(운영일) 당일 봉사자 지정 상태. 지정되지 않았으면 {@code null}.
 *                       웹은 {@code null}이면 "당일 봉사자 지정", {@code ASSIGNED}면 "지정됨(취소)·완료", {@code COMPLETED}면 "완료됨"을 보여준다.
 */
public record VolunteerResponse(
        @Schema(description = "DataGSM 학생 id. 횟수 조정·당일 봉사 API의 경로 값이다", example = "1") Long studentId,
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "화면 표시용 학번", example = "2405") int studentNumber,
        @Schema(description = "호실. 미배정이면 null", example = "301") Integer dormitoryRoom,
        @Schema(description = "앞으로 해야 할 봉사 횟수", example = "2") int volunteerCount,
        @Schema(description = "마지막 횟수 조정 시각. 조정한 적 없으면 null(화면은 -)") Instant lastActivityAt,
        @Schema(description = "오늘 운영일 당일 봉사자 지정 상태. 지정되지 않았으면 null, ASSIGNED(지정됨), COMPLETED(완료됨)", example = "ASSIGNED") DutyStatus todayDuty
) {

    /**
     * @param student        학생
     * @param lastActivityAt 마지막 봉사 횟수 조정 시각. 조정한 적 없으면 {@code null}
     * @param todayDuty      오늘 당일 봉사자 지정 상태. 지정되지 않았으면 {@code null}
     */
    public static VolunteerResponse of(Student student, Instant lastActivityAt, DutyStatus todayDuty) {
        return new VolunteerResponse(
                student.getDatagsmStudentId(),
                student.getName(),
                student.getStudentNumber(),
                student.getDormitoryRoom(),
                student.getVolunteerCount(),
                lastActivityAt,
                todayDuty);
    }
}
