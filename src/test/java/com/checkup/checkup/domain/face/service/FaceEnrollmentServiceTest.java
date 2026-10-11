package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

/**
 * 얼굴 등록이 동의·중복 등록·학생 여부·영상 형식을 AI 호출 전에 확인하고, 성공하면 벡터와 모델 정보만 저장하는지 검증한다.
 */
class FaceEnrollmentServiceTest {
    private static final Long MEMBER_ID = 7L;
    private static final Long STUDENT_ID = 40L;
    private static final AiFaceModel MODEL = new AiFaceModel("model-a", "v1", 256, "l2");

    private final FaceEnrollmentStore enrollmentStore = mock(FaceEnrollmentStore.class);
    private final AiFaceClient aiFaceClient = mock(AiFaceClient.class);
    private final FaceRecognitionService faceRecognitionService = mock(FaceRecognitionService.class);
    private final FaceProperties properties = new FaceProperties(
            "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
            1024, 512, Duration.ofMillis(200), 2, 2, Duration.ofSeconds(1), Duration.ofMinutes(5), 60_000, "v1");
    private final FaceEnrollmentService service = new FaceEnrollmentService(
            enrollmentStore, aiFaceClient, faceRecognitionService, properties, JsonMapper.builder().build());

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
    @DisplayName("기존 등록이 있어도 새 영상을 추출해 재등록한다")
    void alreadyEnrolledCanBeReplaced() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, true));
        List<List<Double>> vectors = List.of(Collections.nCopies(256, 0.0625));
        given(aiFaceClient.extract(any(), eq(MediaType.parseMediaType("video/webm"))))
                .willReturn(new AiFaceEnrollmentResponse(MODEL, 2, 1, vectors));
        given(enrollmentStore.saveOrReplaceTemplate(eq(MEMBER_ID), eq(MODEL), any())).willReturn(STUDENT_ID);

        var response = service.enroll(MEMBER_ID, "video/webm", video());

        assertThat(response.status()).isEqualTo("REGISTERED");
        verify(enrollmentStore).saveOrReplaceTemplate(eq(MEMBER_ID), eq(MODEL), any());
        verify(faceRecognitionService).refreshStudentTemplate(STUDENT_ID);
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
    @DisplayName("초기 등록 성공 시 벡터만 저장하고 활성 세션 갱신을 요청한다")
    void successStoresVectorsAndReturnsStatusOnly() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, false));
        List<List<Double>> vectors = List.of(Collections.nCopies(256, 0.0625));
        given(aiFaceClient.extract(any(), eq(MediaType.parseMediaType("video/webm"))))
                .willReturn(new AiFaceEnrollmentResponse(MODEL, 2, 1, vectors));
        given(enrollmentStore.saveOrReplaceTemplate(eq(MEMBER_ID), eq(MODEL), any())).willReturn(STUDENT_ID);

        var response = service.enroll(MEMBER_ID, "video/webm", video());

        assertThat(response.status()).isEqualTo("REGISTERED");
        verify(enrollmentStore).saveOrReplaceTemplate(eq(MEMBER_ID), eq(MODEL),
                argThat(json -> json.startsWith("[[0.0625,0.0625") && json.endsWith("]]")));
        verify(faceRecognitionService).refreshStudentTemplate(STUDENT_ID);
    }

    @Test
    @DisplayName("AI가 새 템플릿을 만들지 못하면 기존 템플릿과 활성 세션을 건드리지 않는다")
    void invalidAiExtractionLeavesExistingTemplateUntouched() {
        given(enrollmentStore.status(MEMBER_ID))
                .willReturn(new FaceEnrollmentStore.FaceStatusResponseData(true, true, true));
        given(aiFaceClient.extract(any(), eq(MediaType.parseMediaType("video/webm"))))
                .willReturn(new AiFaceEnrollmentResponse(MODEL, 2, 1, List.of()));

        assertThatThrownBy(() -> service.enroll(MEMBER_ID, "video/webm", video()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_AI_BAD_GATEWAY));

        verify(enrollmentStore, never()).saveOrReplaceTemplate(any(), any(), any());
        verify(faceRecognitionService, never()).refreshStudentTemplate(any());
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
