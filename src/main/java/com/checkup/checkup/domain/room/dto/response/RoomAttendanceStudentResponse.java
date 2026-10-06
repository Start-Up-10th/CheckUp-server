package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 호실 전개도의 호실 출석 명단 항목.
 *
 * @param studentId     DataGSM 학생 id. 수동 출석 수정 API의 경로 값이다
 * @param studentName   이름
 * @param studentClass  반
 * @param studentNumber 화면 표시용 학번
 * @param attended      오늘 운영일(08:00 KST 기준) 기숙사 출석 여부
 */
public record RoomAttendanceStudentResponse(
        @Schema(description = "DataGSM 학생 id. 수동 출석 수정 API의 경로 값이다", example = "1") @JsonProperty("student_id") Long studentId,
        @Schema(description = "이름", example = "홍길동") @JsonProperty("student_name") String studentName,
        @Schema(description = "반", example = "4") @JsonProperty("student_class") int studentClass,
        @Schema(description = "화면 표시용 학번", example = "2405") @JsonProperty("student_number") int studentNumber,
        @Schema(description = "오늘 운영일(08:00 KST 기준) 기숙사 출석 여부") @JsonProperty("attended") boolean attended
) {
    public static RoomAttendanceStudentResponse from(Student student, boolean attended) {
        return new RoomAttendanceStudentResponse(
                student.getDatagsmStudentId(),
                student.getName(),
                student.getClassNumber(),
                student.getStudentNumber(),
                attended);
    }
}
