package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학생 호실 명단의 항목.
 *
 * @param studentId     DataGSM 학생 id. 수동 출석 저장 요청의 studentId에 쓴다. 받은 적 없으면 null
 * @param studentName   이름
 * @param studentGrade  학년
 * @param studentClass  반
 * @param studentNumber 학번(예: 2405)
 * @param attended      요청한 용도의 오늘 운영일에 지금 출석 상태인지
 */
public record RoomStudentResponse(
        @Schema(description = "DataGSM 학생 id. 수동 출석 저장 요청의 studentId에 쓴다. 받은 적 없으면 null", example = "1") @JsonProperty("student_id") Long studentId,
        @Schema(description = "이름", example = "홍길동") @JsonProperty("student_name") String studentName,
        @Schema(description = "학년", example = "2") @JsonProperty("student_grade") int studentGrade,
        @Schema(description = "반", example = "4") @JsonProperty("student_class") int studentClass,
        @Schema(description = "화면 표시용 학번", example = "2405") @JsonProperty("student_number") int studentNumber,
        @Schema(description = "요청한 용도의 오늘 운영일(08:00 KST 기준)에 지금 출석 상태인지") @JsonProperty("attended") boolean attended
) {

    public static RoomStudentResponse from(Student student, boolean attended) {
        return new RoomStudentResponse(
                student.getDatagsmStudentId(),
                student.getName(),
                student.getGrade(),
                student.getClassNumber(),
                student.getStudentNumber(),
                attended
        );
    }
}
