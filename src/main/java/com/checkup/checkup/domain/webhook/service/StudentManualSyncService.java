package com.checkup.checkup.domain.webhook.service;

import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import com.checkup.checkup.domain.webhook.dto.response.StudentSyncResponse;
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

/**
 * 관리자 요청으로 DataGSM 학생 OpenAPI에서 전체 학생 목록을 받아 저장된 학생의 정보와 권한을 다시 맞춘다.
 * 웹훅이 재시도 끝에 실패해 놓친 변경(호실, 기숙사 자치위원, 졸업·자퇴 등)을 복구하는 용도다.
 *
 * 반영 로직은 웹훅과 같은 {@link StudentSyncService}를 쓴다. 목록에 없는 학생은 삭제하거나 졸업 처리하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StudentManualSyncService {
    private final AdminVerifier adminVerifier;
    private final DataGsmOpenApiClient dataGsmOpenApiClient;
    private final StudentSyncService studentSyncService;
    private final Clock clock;

    /**
     * 관리자인지 확인한 뒤 DataGSM 학생 목록을 모두 받아 반영한다.
     *
     * DataGSM 요청 중에는 DB 트랜잭션을 잡지 않고, 목록을 다 받은 뒤 {@link StudentSyncService#syncAll}에서 한 트랜잭션으로 반영한다.
     * 목록을 받은 시각을 반영 기준 시각으로 써서, 이보다 새로운 웹훅을 이미 반영한 학생은 건너뛴다.
     *
     * @param memberId 세션의 회원 id
     * @return 받은 학생 수와 반영한 학생 수
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         DataGSM 요청이 실패하면 {@link ErrorCode#DATAGSM_ERROR}(502)
     */
    public StudentSyncResponse sync(Long memberId) {
        adminVerifier.verify(memberId);
        List<Student> students = fetchAll();
        List<StudentSyncData> data = students.stream().map(StudentManualSyncService::toSyncData).toList();
        int synced = studentSyncService.syncAll(data, clock.instant());
        return new StudentSyncResponse(data.size(), synced);
    }

    /**
     * DataGSM OpenAPI 학생을 공통 동기화 정보로 바꾼다. 졸업·자퇴생은 학년 등이 비어 있어 {@code null}로 넣는다.
     */
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

    /**
     * 졸업·자퇴생을 포함한 학생 목록을 마지막 페이지까지 받는다. 한 페이지라도 실패하면 아무것도 반영하지 않도록 예외를 던진다.
     *
     * @throws CustomException DataGSM 요청이 실패하면 {@link ErrorCode#DATAGSM_ERROR}(502)
     */
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
