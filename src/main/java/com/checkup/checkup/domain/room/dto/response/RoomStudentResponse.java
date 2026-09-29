package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.member.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 학생 호실 명단의 항목. */
public record RoomStudentResponse(
        @JsonProperty("student_name") String studentName,
        @JsonProperty("student_class") int studentClass,
        @JsonProperty("student_number") int studentNumber
) {

    public static RoomStudentResponse from(Student student) {
        return new RoomStudentResponse(
                student.getMember().getName(),
                student.getClassNumber(),
                student.getStudentNumber()
        );
    }
}
