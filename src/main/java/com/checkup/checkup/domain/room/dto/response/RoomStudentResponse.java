package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 학생 호실 명단의 항목.
 *
 * @param studentName   이름
 * @param studentClass  반
 * @param studentNumber 학번(예: 2405)
 * @param attended      요청한 용도의 오늘 운영일에 지금 출석 상태인지
 */
public record RoomStudentResponse(
        @JsonProperty("student_name") String studentName,
        @JsonProperty("student_class") int studentClass,
        @JsonProperty("student_number") int studentNumber,
        @JsonProperty("attended") boolean attended
) {

    public static RoomStudentResponse from(Student student, boolean attended) {
        return new RoomStudentResponse(
                student.getName(),
                student.getClassNumber(),
                student.getStudentNumber(),
                attended
        );
    }
}
