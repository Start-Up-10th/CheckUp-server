package com.checkup.checkup.domain.member.service;

import com.checkup.checkup.domain.member.dto.StudentSyncData;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.themoment.datagsm.sdk.openapi.exception.DataGsmException;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.client.StudentApi;
import team.themoment.datagsm.sdk.openapi.model.EnrollmentFilter;
import team.themoment.datagsm.sdk.openapi.model.Student;
import team.themoment.datagsm.sdk.openapi.model.StudentResponse;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

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

    private List<Student> fetchAll() {
        int page = 0;
        List<Student> students = new ArrayList<>();
        try {
            StudentResponse response;
            do {
                StudentApi.StudentRequest request = new StudentApi.StudentRequest()
                        .page(page)
                        .size(300)
                        .enrollmentFilter(EnrollmentFilter.detailed()
                                .includeGraduates(true)
                                .includeWithdrawn(true));
                response = dataGsmOpenApiClient.students().getStudents(request);
                students.addAll(response.getStudents());
                page++;
            } while (page < response.getTotalPages());
        } catch (DataGsmException e) {
            throw new CustomException(ErrorCode.DATAGSM_ERROR);
        }
        return students;
    }
}
