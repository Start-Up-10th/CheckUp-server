package com.checkup.checkup.domain.volunteer.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 날짜별 당일 봉사자 기록의 학생 한 명(#250).
 *
 * @param studentId     DataGSM 학생 id
 * @param studentNumber 학번
 * @param name          이름
 * @param dormitoryRoom 호실. 미배정이면 {@code null}
 * @param status        그날 지정 상태. 운영일이 지났는데 완료·미이행 처리가 없던 지정은 {@code MISSED}다
 * @param adjusted      미이행으로 횟수를 처리했는지
 * @param addedCount    미이행으로 실제로 바꾼 횟수. 처리 전이면 {@code null}이고 음수일 수 있다
 * @param reason        미이행 처리 사유. 없으면 {@code null}
 */
public record VolunteerDutyRecordResponse(
        @Schema(description = "DataGSM 학생 id", example = "3203") Long studentId,
        @Schema(description = "화면 표시용 학번", example = "3203") int studentNumber,
        @Schema(description = "이름", example = "류하준") String name,
        @Schema(description = "호실. 미배정이면 null", example = "302") Integer dormitoryRoom,
        @Schema(description = "그날 지정 상태. ASSIGNED(오늘 지정, 처리 전), COMPLETED(완료), MISSED(미이행). 운영일이 지났는데 처리가 없던 지정은 MISSED", example = "MISSED") DutyStatus status,
        @Schema(description = "미이행으로 횟수를 처리했는지", example = "true") boolean adjusted,
        @Schema(description = "미이행으로 실제로 바꾼 횟수. 처리 전이면 null, 음수일 수 있다", example = "1") Integer addedCount,
        @Schema(description = "미이행 처리 사유. 없으면 null", example = "당일 봉사 미이행") String reason
) {

    /**
     * @param student    학생
     * @param status     조회할 때 본 지정 상태
     * @param adjustment 그 지정의 미이행 조정 기록. 횟수를 처리하지 않았으면 {@code null}
     */
    public static VolunteerDutyRecordResponse of(Student student, DutyStatus status, VolunteerAdjustment adjustment) {
        return new VolunteerDutyRecordResponse(
                student.getDatagsmStudentId(),
                student.getStudentNumber(),
                student.getName(),
                student.getDormitoryRoom(),
                status,
                adjustment != null,
                adjustment == null ? null : (int) adjustment.getDelta(),
                adjustment == null ? null : adjustment.getReason());
    }
}
