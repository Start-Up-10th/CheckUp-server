package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceEnrollmentResponse;
import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FaceEnrollmentServiceTest {
    private static final Long MEMBER_ID = 7L;
    private static final AiFaceModel MODEL = new AiFaceModel("model-a", "v1", 256, "l2");

    private final FaceEnrollmentStore enrollmentStore = mock(FaceEnrollmentStore.class);
    private final AiFaceClient aiFaceClient = mock(AiFaceClient.class);
    private final FaceProperties properties = new FaceProperties(
            "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
            1024, 512, Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
    private final FaceEnrollmentService service = new FaceEnrollmentService(
            enrollmentStore, aiFaceClient, properties, JsonMapper.builder().build());

    @Test
    @DisplayName("동의가 없으면 AI를 호출하지 않는다")
    void withoutConsentDoesNotCallAi() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(false, false, false));

        assertThatThrownBy(() -> service.enroll(MEMBER_ID, "video/webm", video()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_CONSENT_REQUIRED));

        verify(aiFaceClient, never()).extract(any(), any());
    }

    @Test
    @DisplayName("기존 등록이 있으면 AI를 호출하지 않는다")
    void alreadyEnrolledDoesNotCallAi() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, true));

        assertThatThrownBy(() -> service.enroll(MEMBER_ID, "video/webm", video()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_ALREADY_REGISTERED));

        verify(aiFaceClient, never()).extract(any(), any());
    }

    @Test
    @DisplayName("등록 대상 학생이 아니면 AI를 호출하지 않는다")
    void nonStudentDoesNotCallAi() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, false, false));

        assertThatThrownBy(() -> service.enroll(MEMBER_ID, "video/webm", video()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_ENROLLMENT_NOT_ELIGIBLE));

        verify(aiFaceClient, never()).extract(any(), any());
    }

    @Test
    @DisplayName("성공은 벡터와 모델정보를 저장하고 브라우저에는 상태만 준다")
    void successStoresVectorsAndReturnsStatusOnly() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, false));
        List<List<Double>> vectors = List.of(Collections.nCopies(256, 0.0625));
        given(aiFaceClient.extract(any(), eq(MediaType.parseMediaType("video/webm"))))
                .willReturn(new AiFaceEnrollmentResponse(MODEL, 2, 1, vectors));

        var response = service.enroll(MEMBER_ID, "video/webm", video());

        assertThat(response.status()).isEqualTo("REGISTERED");
        verify(enrollmentStore).saveTemplate(eq(MEMBER_ID), eq(MODEL),
                argThat(json -> json.startsWith("[[0.0625,0.0625") && json.endsWith("]]")));
    }

    @Test
    @DisplayName("잘못된 MIME은 AI로 전달하지 않는다")
    void invalidMimeIsNotSentToAi() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, false));

        assertThatThrownBy(() -> service.enroll(MEMBER_ID, "video/x-msvideo", new byte[]{1, 2, 3}))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_INVALID_MEDIA));
        verify(aiFaceClient, never()).extract(any(), any());
    }

    private static byte[] video() {
        return new byte[]{1, 2, 3};
    }
}
