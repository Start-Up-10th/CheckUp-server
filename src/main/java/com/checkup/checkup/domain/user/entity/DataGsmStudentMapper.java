package com.checkup.checkup.domain.user.entity;

public final class DataGsmStudentMapper {

    private DataGsmStudentMapper() {
    }

    public static Sex toSex(team.themoment.datagsm.sdk.openapi.model.Sex sdk) {
        if (sdk == null) {
            return null;
        }
        return switch (sdk) {
            case MAN -> Sex.MAN;
            case WOMAN -> Sex.WOMAN;
        };
    }

    public static StudentRole toRole(team.themoment.datagsm.sdk.openapi.model.StudentRole sdk) {
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
