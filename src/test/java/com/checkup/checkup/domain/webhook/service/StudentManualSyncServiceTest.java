package com.checkup.checkup.domain.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import com.checkup.checkup.domain.webhook.dto.response.StudentSyncResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;
import team.themoment.datagsm.sdk.openapi.client.StudentApi;
import team.themoment.datagsm.sdk.openapi.exception.DataGsmException;
import team.themoment.datagsm.sdk.openapi.model.Student;
import team.themoment.datagsm.sdk.openapi.model.StudentResponse;
import team.themoment.datagsm.sdk.openapi.model.StudentRole;

/**
 * 수동 동기화가 관리자만 허용하고, 졸업·자퇴생을 포함한 학생 목록을 마지막 페이지까지 받아
 * 공통 동기화 정보로 바꿔 {@link StudentSyncService}에 넘기는지 검증한다.
 * DataGSM 요청이 한 페이지라도 실패하면 아무것도 반영하지 않고 502로 끝나는지도 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class StudentManualSyncServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    private AdminVerifier adminVerifier;

    @Mock
    private DataGsmOpenApiClient dataGsmOpenApiClient;

    @Mock
    private StudentApi studentApi;

    @Mock
    private StudentSyncService studentSyncService;

    private StudentManualSyncService service;

    @BeforeEach
    void setUp() {
        service = new StudentManualSyncService(
                adminVerifier, dataGsmOpenApiClient, studentSyncService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("마지막 페이지까지 받아 모든 학생을 목록을 받은 시각으로 반영하고 받은 수와 반영 수를 돌려준다")
    void allPagesAreFetchedAndSynced() {
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudents(any())).willReturn(
                page(2, enrolled(1L), enrolled(2L)),
                page(2, enrolled(3L)));
        given(studentSyncService.syncAll(any(), any())).willReturn(2);

        StudentSyncResponse response = service.sync(MEMBER_ID);

        ArgumentCaptor<StudentApi.StudentRequest> requests = ArgumentCaptor.forClass(StudentApi.StudentRequest.class);
        verify(studentApi, times(2)).getStudents(requests.capture());
        assertThat(requests.getAllValues()).extracting(StudentApi.StudentRequest::getPage).containsExactly(0, 1);

        List<StudentSyncData> synced = capturedSyncData();
        assertThat(synced).extracting(StudentSyncData::datagsmStudentId).containsExactly(1L, 2L, 3L);
        verify(studentSyncService).syncAll(any(), eq(NOW));
        assertThat(response).isEqualTo(new StudentSyncResponse(3, 2));
    }

    @Test
    @DisplayName("졸업·자퇴생도 받도록 요청한다")
    void graduatesAndWithdrawnAreRequested() {
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudents(any())).willReturn(page(1));

        service.sync(MEMBER_ID);

        ArgumentCaptor<StudentApi.StudentRequest> request = ArgumentCaptor.forClass(StudentApi.StudentRequest.class);
        verify(studentApi).getStudents(request.capture());
        Map<String, String> params = new HashMap<>();
        request.getValue().getEnrollmentFilter().applyToParams(params);
        assertThat(params)
                .containsEntry("onlyEnrolled", "false")
                .containsEntry("includeGraduates", "true")
                .containsEntry("includeWithdrawn", "true");
    }

    @Test
    @DisplayName("재학생은 모든 값을, 졸업생은 비어 있는 학년·호실을 null로 바꿔 넘긴다")
    void sdkStudentIsConvertedToSyncData() {
        Student graduate = new Student();
        graduate.setId(2L);
        graduate.setName("졸업생");
        graduate.setRole(StudentRole.GRADUATE);
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudents(any())).willReturn(page(1, enrolled(1L), graduate));

        service.sync(MEMBER_ID);

        assertThat(capturedSyncData()).containsExactly(
                new StudentSyncData(1L, "학생1", 2, 1, 5, 2105, 301, "DORMITORY_MANAGER"),
                new StudentSyncData(2L, "졸업생", null, null, null, null, null, "GRADUATE"));
    }

    @Test
    @DisplayName("관리자가 아니면 DataGSM을 호출하지 않고 403으로 끝난다")
    void nonAdminIsRejectedBeforeDataGsmCall() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(MEMBER_ID);

        assertThatThrownBy(() -> service.sync(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
        verify(dataGsmOpenApiClient, never()).students();
        verify(studentSyncService, never()).syncAll(any(), any());
    }

    @Test
    @DisplayName("중간 페이지에서 DataGSM 요청이 실패하면 아무것도 반영하지 않고 502로 끝난다")
    void dataGsmFailureSyncsNothing() {
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudents(any()))
                .willReturn(page(2, enrolled(1L)))
                .willThrow(new DataGsmException("fail"));

        assertThatThrownBy(() -> service.sync(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_ERROR));
        verify(studentSyncService, never()).syncAll(any(), any());
    }

    @Test
    @DisplayName("중간 페이지에서 DataGSM 응답 시간이 초과되면 아무것도 반영하지 않고 503으로 끝난다")
    void dataGsmTimeoutSyncsNothing() {
        given(dataGsmOpenApiClient.students()).willReturn(studentApi);
        given(studentApi.getStudents(any()))
                .willReturn(page(2, enrolled(1L)))
                .willThrow(new DataGsmException("fail", new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> service.sync(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATAGSM_UNAVAILABLE));
        verify(studentSyncService, never()).syncAll(any(), any());
    }

    @SuppressWarnings("unchecked")
    private List<StudentSyncData> capturedSyncData() {
        ArgumentCaptor<List<StudentSyncData>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentSyncService).syncAll(captor.capture(), any());
        return captor.getValue();
    }

    private static StudentResponse page(int totalPages, Student... students) {
        StudentResponse response = new StudentResponse();
        response.setStudents(List.of(students));
        response.setTotalPages(totalPages);
        return response;
    }

    /** 재학생 한 명. 학년 2, 반 1, 번호 5, 학번 2105, 호실 301, 기숙사 자치위원. */
    private static Student enrolled(Long id) {
        Student student = new Student();
        student.setId(id);
        student.setName("학생" + id);
        student.setGrade(2);
        student.setClassNum(1);
        student.setNumber(5);
        student.setStudentNumber(2105);
        student.setDormitoryRoom(301);
        student.setRole(StudentRole.DORMITORY_MANAGER);
        return student;
    }
}
