package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomAttendanceStudentResponse(
        @JsonProperty("student_id") Long studentId,
        @JsonProperty("student_name") String studentName,
        @JsonProperty("student_class") int studentClass,
        @JsonProperty("student_number") int studentNumber,
        @JsonProperty("attended") boolean attended
) {
    public static RoomAttendanceStudentResponse from(Student student, boolean attended) {
        return new RoomAttendanceStudentResponse(
                student.getDatagsmStudentId(),
                student.getMember().getName(),
                student.getClassNumber(),
                student.getStudentNumber(),
                attended);
    }
}
