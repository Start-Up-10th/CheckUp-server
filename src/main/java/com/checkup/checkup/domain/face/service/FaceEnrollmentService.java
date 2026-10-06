package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceEnrollmentResponse;
import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.dto.FaceEnrollmentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 원본 영상을 AI로 보내기 전에 로그인 회원과 동의 여부를 확인한다. */
@Service
@RequiredArgsConstructor
public class FaceEnrollmentService {
    private static final int VECTOR_DIMENSION = 256;
    private static final int MAX_VECTORS = 20;

    private final FaceEnrollmentStore enrollmentStore;
    private final AiFaceClient aiFaceClient;
    private final FaceProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 휴대폰 촬영 영상으로 학생의 얼굴을 처음 등록한다(REQ-FACE-001).
     * 동의·등록 대상·중복 등록을 먼저 확인한 뒤 AI에서 대표 벡터를 뽑아 저장한다. 원본 영상은 처리 뒤 메모리에서 지우고 저장하지 않는다.
     *
     * @param contentType {@code video/webm} 또는 {@code video/mp4}
     * @param rawVideo    원본 영상. 처리 뒤 0으로 덮어쓴다
     * @return 등록 결과
     * @throws CustomException 필수 동의가 없으면 {@link ErrorCode#FACE_CONSENT_REQUIRED}(403),
     *                         등록 대상이 아니면 {@link ErrorCode#FACE_ENROLLMENT_NOT_ELIGIBLE}(403),
     *                         이미 등록했으면 {@link ErrorCode#FACE_ALREADY_REGISTERED},
     *                         영상이 없거나 형식이 다르면 {@link ErrorCode#FACE_INVALID_MEDIA},
     *                         너무 크면 {@link ErrorCode#FACE_UPLOAD_TOO_LARGE},
     *                         AI 응답이 계약과 다르면 {@link ErrorCode#FACE_AI_BAD_GATEWAY}
     */
    public FaceEnrollmentResponse enroll(Long memberId, String contentType, byte[] rawVideo) {
        try {
            FaceEnrollmentStore.FaceStatusResponseData status = enrollmentStore.status(memberId);
            if (!status.consented()) {
                throw new CustomException(ErrorCode.FACE_CONSENT_REQUIRED);
            }
            if (!status.eligible()) {
                throw new CustomException(ErrorCode.FACE_ENROLLMENT_NOT_ELIGIBLE);
            }
            if (status.enrolled()) {
                throw new CustomException(ErrorCode.FACE_ALREADY_REGISTERED);
            }
            if (rawVideo == null || rawVideo.length == 0) {
                throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
            }
            if (rawVideo.length > properties.maxUploadBytes()) {
                throw new CustomException(ErrorCode.FACE_UPLOAD_TOO_LARGE);
            }

            MediaType mediaType = mediaType(contentType);
            // AI 네트워크 호출은 짧은 저장 트랜잭션보다 먼저 한다. 동시 요청 경합은 DB unique 제약이 막는다.
            AiFaceEnrollmentResponse extracted = aiFaceClient.extract(rawVideo, mediaType);
            validate(extracted);
            String vectorsJson;
            try {
                vectorsJson = objectMapper.writeValueAsString(extracted.vectors());
            } catch (JacksonException e) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            try {
                enrollmentStore.saveTemplate(memberId, extracted.model(), vectorsJson);
            } catch (DataIntegrityViolationException e) {
                throw new CustomException(ErrorCode.FACE_ALREADY_REGISTERED);
            }
            return FaceEnrollmentResponse.registered();
        } finally {
            if (rawVideo != null) {
                Arrays.fill(rawVideo, (byte) 0);
            }
        }
    }

    private static MediaType mediaType(String raw) {
        if (raw == null) {
            throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        }
        String normalized = raw.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (normalized) {
            case "video/webm" -> MediaType.parseMediaType("video/webm");
            case "video/mp4" -> MediaType.parseMediaType("video/mp4");
            default -> throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        };
    }

    private static void validate(AiFaceEnrollmentResponse response) {
        if (response == null || response.model() == null || response.vectors() == null) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
        AiFaceModel model = response.model();
        if (model.modelId() == null || model.modelId().isBlank()
                || model.version() == null || model.version().isBlank()
                || model.dimension() != VECTOR_DIMENSION
                || !"l2".equals(model.normalization())
                || response.reviewedFrames() < 0 || response.reviewedFrames() > 100
                || response.acceptedFrames() < 0 || response.acceptedFrames() > response.reviewedFrames()
                || response.vectors().isEmpty() || response.vectors().size() > MAX_VECTORS
                || response.acceptedFrames() < response.vectors().size()) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
        for (List<Double> vector : response.vectors()) {
            if (vector == null || vector.size() != VECTOR_DIMENSION
                    || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            double normSquared = vector.stream().mapToDouble(value -> value * value).sum();
            if (Math.abs(Math.sqrt(normSquared) - 1.0) > 0.01) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
        }
    }
}
