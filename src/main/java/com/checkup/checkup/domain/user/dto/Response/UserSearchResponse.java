package com.checkup.checkup.domain.user.dto.Response;

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
                toSex(student.getSex()),
                student.getGrade().orElse(null),
                student.getClassNum().orElse(null),
                student.getNumber().orElse(null),
                toRole(student.getRole())
        );
    }

    private static Sex toSex(team.themoment.datagsm.sdk.openapi.model.Sex sdk) {
        if (sdk == null) {
            return null;
        }
        return switch (sdk) {
            case MAN -> Sex.MAN;
            case WOMAN -> Sex.WOMAN;
        };
    }

    private static StudentRole toRole(team.themoment.datagsm.sdk.openapi.model.StudentRole sdk) {
        if (sdk == null) {
            return null;
        }
        return switch (sdk) {
            case GENERAL_STUDENT -> StudentRole.GENERAL_STUDENT;
            case STUDENT_COUNCIL -> StudentRole.STUDENT_COUNCIL;
            case DORMITORY_MANAGER -> StudentRole.DORMITORY_MANAGER;
            case GRADUATE -> StudentRole.GRADUATE;
            case WITHDRAWN -> StudentRole.WITHDRAWN;
        };
    }
}
