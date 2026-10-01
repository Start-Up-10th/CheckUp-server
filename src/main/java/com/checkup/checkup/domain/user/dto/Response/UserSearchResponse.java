package com.checkup.checkup.domain.user.dto.Response;

import com.checkup.checkup.domain.user.entity.DataGsmStudentMapper;
import com.checkup.checkup.domain.user.entity.Sex;
import com.checkup.checkup.domain.user.entity.StudentRole;
import team.themoment.datagsm.sdk.openapi.model.Student;

public record UserSearchResponse(
        Long id,
        String name,
        String email,
        Integer dormitoryRoom,
        Sex sex,
        Integer grade,
        Integer classNumber,
        Integer number,
        StudentRole studentRole
) {

    public static UserSearchResponse from(Student student) {
        return new UserSearchResponse(
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getDormitoryRoom().orElse(null),
                DataGsmStudentMapper.toSex(student.getSex()),
                student.getGrade().orElse(null),
                student.getClassNum().orElse(null),
                student.getNumber().orElse(null),
                DataGsmStudentMapper.toRole(student.getRole())
        );
    }
}
