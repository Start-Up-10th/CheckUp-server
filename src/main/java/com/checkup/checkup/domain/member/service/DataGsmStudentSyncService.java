package com.checkup.checkup.domain.member.service;

import com.checkup.checkup.domain.member.dto.StudentSyncData;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.model.Student;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class DataGsmStudentSyncService {
    private final AdminVerifier adminVerifier;
    private final DataGsmOpenApiClient dataGsmOpenApiClient;
    private final StudentSyncService studentSyncService;
    private final Clock clock;

    private static StudentSyncData toSyncData(Student s) {
        return new StudentSyncData(
                s.getId(),
                s.getName(),
                s.getGrade().orElse(null),
                s.getClassNum().orElse(null),
                s.getNumber().orElse(null),
                s.getStudentNumber().orElse(null),
                s.getDormitoryRoom().orElse(null),
                s.getRole() == null ? null : s.getRole().name()
        );
    }
}
