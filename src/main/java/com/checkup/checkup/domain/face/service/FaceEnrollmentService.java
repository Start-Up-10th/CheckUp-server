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
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Verifies the session owner and consent before sending raw video to AI. */
@Service
@RequiredArgsConstructor
public class FaceEnrollmentService {
    private static final int VECTOR_DIMENSION = 256;
    private static final int MAX_VECTORS = 20;

    private final FaceEnrollmentStore enrollmentStore;
    private final AiFaceClient aiFaceClient;
    private final FaceProperties properties;
    private final ObjectMapper objectMapper;

    public FaceEnrollmentResponse enroll(Long memberId, MultipartFile video) {
        FaceEnrollmentStore.FaceStatusResponseData status = enrollmentStore.status(memberId);
        if (!status.consented()) {
            throw new CustomException(ErrorCode.FACE_CONSENT_REQUIRED);
        }
        if (status.enrolled()) {
            throw new CustomException(ErrorCode.FACE_ALREADY_REGISTERED);
        }
        if (video == null || video.isEmpty()) {
            throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        }
        if (video.getSize() > properties.maxUploadBytes()) {
            throw new CustomException(ErrorCode.FACE_UPLOAD_TOO_LARGE);
        }

        MediaType mediaType = mediaType(video.getContentType());
        byte[] rawVideo;
        try {
            rawVideo = video.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        }
        if (rawVideo.length == 0 || rawVideo.length > properties.maxUploadBytes()) {
            throw new CustomException(rawVideo.length == 0
                    ? ErrorCode.FACE_INVALID_MEDIA : ErrorCode.FACE_UPLOAD_TOO_LARGE);
        }

        // Network call occurs before the short insert transaction. A unique DB constraint closes races.
        AiFaceEnrollmentResponse extracted;
        try {
            extracted = aiFaceClient.extract(rawVideo, mediaType);
        } finally {
            Arrays.fill(rawVideo, (byte) 0);
        }
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
                || response.vectors().isEmpty() || response.vectors().size() > MAX_VECTORS) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
        for (List<Double> vector : response.vectors()) {
            if (vector == null || vector.size() != VECTOR_DIMENSION
                    || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
        }
    }
}
