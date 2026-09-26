package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.MemberRole;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

public record OAuthLoginResponse(
        String name,
        MemberRole role,
        Integer dormitoryRoom) {
    public static OAuthLoginResponse from(UserInfo userInfo, MemberRole role) {
        Student student = userInfo.getStudent();
        if (student != null) {
            return new OAuthLoginResponse(student.getName(),role, student.getDormitoryRoom());
        }
        return new OAuthLoginResponse(userInfo.getTeacher().getName(), role, null);
    }
}
