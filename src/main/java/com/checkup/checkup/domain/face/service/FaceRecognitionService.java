package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.face.ai.AiFaceCandidate;
import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.ai.AiFaceException;
import com.checkup.checkup.domain.face.ai.AiFaceFrameResponse;
import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.face.ai.AiFaceSessionRequest;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.dto.FaceFrameResponse;
import com.checkup.checkup.domain.face.dto.FaceSessionResponse;
import com.checkup.checkup.domain.face.entity.FaceTemplate;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Starts AI sessions, rate limits camera frames, and authorizes face matches before attendance. */
@Slf4j
@Service
public class FaceRecognitionService {
    private static final int MAX_CANDIDATES = 200;
    private static final int VECTOR_DIMENSION = 256;

    private final FaceTemplateRepository faceTemplateRepository;
    private final FaceSessionStore faceSessionStore;
    private final AiFaceClient aiFaceClient;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;
    private final AdminVerifier adminVerifier;
    private final FaceProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Semaphore framePermits;

    public FaceRecognitionService(
            FaceTemplateRepository faceTemplateRepository,
            FaceSessionStore faceSessionStore,
            AiFaceClient aiFaceClient,
            StudentRepository studentRepository,
            AttendanceService attendanceService,
            AdminVerifier adminVerifier,
            FaceProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.faceTemplateRepository = faceTemplateRepository;
        this.faceSessionStore = faceSessionStore;
        this.aiFaceClient = aiFaceClient;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
        this.adminVerifier = adminVerifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.framePermits = new Semaphore(properties.maxConcurrentFrames());
    }

    public FaceSessionResponse create(Long adminMemberId, AttendancePurpose purpose) {
        adminVerifier.verify(adminMemberId);
        List<FaceTemplate> templates = eligibleTemplates();
        if (templates.isEmpty()) {
            throw new CustomException(ErrorCode.FACE_NO_CANDIDATES);
        }
        if (templates.size() > MAX_CANDIDATES) {
            throw new CustomException(ErrorCode.FACE_TOO_MANY_CANDIDATES);
        }
        AiFaceSessionRequest request = sessionRequest(templates);
        UUID sessionId = UUID.randomUUID();
        try {
            aiFaceClient.createSession(sessionId, request);
        } catch (RuntimeException e) {
            deleteAiSessionQuietly(sessionId);
            throw e;
        }

        Instant now = clock.instant();
        try {
            // Only persist after AI is ready; never hold a database transaction across inference setup.
            faceSessionStore.create(sessionId, adminMemberId, purpose,
                    templates.stream().map(template -> template.getStudent().getId()).toList(),
                    now, now.minus(properties.minFrameInterval()));
        } catch (RuntimeException e) {
            deleteAiSessionQuietly(sessionId);
            throw e;
        }
        return new FaceSessionResponse(sessionId, purpose, "ready");
    }

    public FaceFrameResponse recognize(
            Long adminMemberId,
            UUID sessionId,
            String frameIdHeader,
            String contentType,
            byte[] image
    ) {
        adminVerifier.verify(adminMemberId);
        if (image == null || image.length == 0) {
            throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        }
        if (image.length > properties.maxFrameBytes()) {
            throw new CustomException(ErrorCode.FACE_UPLOAD_TOO_LARGE);
        }
        MediaType mediaType = frameMediaType(contentType);
        String frameId = normalizeFrameId(frameIdHeader);
        if (!framePermits.tryAcquire()) {
            throw new CustomException(ErrorCode.FACE_SERVICE_BUSY);
        }
        try {
            FaceSessionView session = faceSessionStore.findOwned(sessionId, adminMemberId);
            Instant now = clock.instant();
            if (!session.lastActivityAt().isAfter(now.minus(properties.sessionIdleTimeout()))) {
                closeOwned(session);
                throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
            }
            if (session.candidateStudentIds().stream()
                    .anyMatch(id -> !studentRepository.existsByIdAndDormitoryRoomIsNotNull(id))) {
                closeOwned(session);
                throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
            }
            boolean claimed = faceSessionStore.claimFrame(sessionId, adminMemberId, now,
                    now.minus(properties.minFrameInterval()));
            if (!claimed) {
                throw new CustomException(ErrorCode.FACE_FRAME_RATE_LIMITED);
            }

            AiFaceFrameResponse result;
            try {
                result = aiFaceClient.recognize(sessionId, frameId, image, mediaType);
            } catch (AiFaceException e) {
                if (e.getStatus() != 404 || !"frame".equals(e.getOperation())) {
                    throw e;
                }
                // Contract says 404 means session absent, so this frame was not processed; recreate and retry once.
                try {
                    aiFaceClient.createSession(sessionId, candidateRequest(session.candidateStudentIds()));
                    result = aiFaceClient.recognize(sessionId, frameId, image, mediaType);
                } catch (RuntimeException recoveryFailure) {
                    faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
                    throw recoveryFailure;
                }
            }
            if (!frameId.equals(result.frameId())) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            FaceSessionView currentSession;
            try {
                currentSession = faceSessionStore.findOwned(sessionId, adminMemberId);
            } catch (CustomException closed) {
                deleteAiSessionQuietly(sessionId);
                throw closed;
            }
            return new FaceFrameResponse(frameId, result.faces().stream()
                    .map(face -> toPublicFace(currentSession, face)).toList());
        } catch (AiFaceException e) {
            if (e.getStatus() >= 500 || e.getStatus() == 404) {
                faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
            }
            throw e;
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.FACE_AI_BAD_GATEWAY) {
                faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
            }
            throw e;
        } finally {
            framePermits.release();
            if (image != null) {
                Arrays.fill(image, (byte) 0);
            }
        }
    }

    /** Idempotent browser close; remote deletion is retried if the DB mapping remains. */
    public void close(Long adminMemberId, UUID sessionId) {
        adminVerifier.verify(adminMemberId);
        faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwned);
    }

    public void closeAll(Long adminMemberId) {
        faceSessionStore.findForAdmin(adminMemberId).forEach(this::closeOwnedQuietly);
    }

    public void removeStudent(Long studentId) {
        faceSessionStore.findWithStudent(studentId).forEach(this::closeOwnedQuietly);
    }

    public void cleanupIdleSessions() {
        Instant cutoff = clock.instant().minus(properties.sessionIdleTimeout());
        faceSessionStore.findIdleBefore(cutoff).forEach(session -> {
            if (!session.active() || faceSessionStore.markInactiveIfIdle(session.id(), cutoff)) {
                closeOwnedQuietly(session);
            }
        });
    }

    private FaceFrameResponse.Face toPublicFace(FaceSessionView session, AiFaceFrameResponse.FaceResult face) {
        if (face == null || face.trackId() == null || face.quality() == null || face.recognition() == null) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
        String status = face.recognition().status();
        if (status == null || !Set.of("KNOWN", "UNKNOWN", "NOT_ATTEMPTED").contains(status)
                || face.bbox() == null || face.bbox().size() != 4
                || face.bbox().stream().anyMatch(value -> value == null || !Double.isFinite(value))
                || face.landmarks() == null
                || face.landmarks().stream().anyMatch(point -> point == null || point.size() != 2
                    || point.stream().anyMatch(value -> value == null || !Double.isFinite(value)))
                || qualityInvalid(face.quality()) || face.attempts() < 0
                || notFinite(face.recognition().score()) || notFinite(face.recognition().margin())) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }

        String studentId = null;
        String attendance = null;
        if ("KNOWN".equals(status)) {
            Long dataGsmStudentId = canonicalStudentId(face.recognition().studentId());
            var student = dataGsmStudentId == null ? java.util.Optional.<Student>empty()
                    : studentRepository.findByDatagsmStudentId(dataGsmStudentId);
            if (student.isPresent()
                    && session.candidateStudentIds().contains(student.get().getId())
                    && student.get().getDormitoryRoom() != null) {
                studentId = dataGsmStudentId.toString();
                AttendanceRecordResult recorded = attendanceService.markAttended(
                        student.get().getId(), session.purpose(), clock.instant(), AttendanceMethod.FACE);
                attendance = attendanceResult(recorded);
            } else {
                // Never let AI nominate a student outside the Spring-owned, current session target list.
                status = "UNKNOWN";
            }
        }

        AiFaceFrameResponse.Quality quality = face.quality();
        return new FaceFrameResponse.Face(face.trackId(), face.bbox(), face.landmarks(),
                new FaceFrameResponse.Quality(quality.brightness(), quality.sharpness(), quality.issues()),
                new FaceFrameResponse.Recognition(status, studentId,
                        face.recognition().score(), face.recognition().margin(), attendance),
                face.attempts(), face.qrRecommended());
    }

    private static String attendanceResult(AttendanceRecordResult result) {
        return switch (result) {
            case RECORDED -> "RECORDED";
            case ALREADY_ATTENDED, SUPERSEDED_BY_MANUAL -> "DUPLICATE";
            case STALE -> "STALE";
            case FUTURE -> "REJECTED";
        };
    }

    private static boolean qualityInvalid(AiFaceFrameResponse.Quality quality) {
        return !Double.isFinite(quality.brightness()) || !Double.isFinite(quality.sharpness())
                || quality.issues() == null;
    }

    private static boolean notFinite(Double value) {
        return value != null && !Double.isFinite(value);
    }

    private static Long canonicalStudentId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<FaceTemplate> eligibleTemplates() {
        return faceTemplateRepository.findAllByOrderByStudent_IdAsc().stream()
                .filter(template -> template.getStudent().getFaceConsentAt() != null)
                .filter(template -> template.getStudent().getDormitoryRoom() != null)
                .filter(template -> template.getStudent().getDatagsmStudentId() != null)
                .toList();
    }

    private AiFaceSessionRequest sessionRequest(List<FaceTemplate> templates) {
        AiFaceModel model = modelOf(templates.getFirst());
        List<AiFaceCandidate> candidates = new ArrayList<>(templates.size());
        for (FaceTemplate template : templates) {
            if (!modelMatches(model, template)) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            candidates.add(new AiFaceCandidate(template.getStudent().getDatagsmStudentId().toString(),
                    parseVectors(template.getVectorsJson())));
        }
        return new AiFaceSessionRequest(model, candidates);
    }

    private AiFaceSessionRequest candidateRequest(Set<Long> studentIds) {
        List<FaceTemplate> templates = faceTemplateRepository.findAllByStudent_IdIn(studentIds).stream()
                .filter(template -> template.getStudent().getFaceConsentAt() != null)
                .filter(template -> template.getStudent().getDormitoryRoom() != null)
                .filter(template -> template.getStudent().getDatagsmStudentId() != null)
                .toList();
        if (templates.size() != studentIds.size() || templates.size() > MAX_CANDIDATES || templates.isEmpty()) {
            throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
        }
        return sessionRequest(templates);
    }

    private static AiFaceModel modelOf(FaceTemplate template) {
        return new AiFaceModel(template.getModelId(), template.getModelVersion(),
                template.getDimension(), template.getNormalization());
    }

    private static boolean modelMatches(AiFaceModel model, FaceTemplate template) {
        return model.modelId() != null && !model.modelId().isBlank()
                && model.version() != null && !model.version().isBlank()
                && Objects.equals(model.modelId(), template.getModelId())
                && Objects.equals(model.version(), template.getModelVersion())
                && model.dimension() == template.getDimension()
                && Objects.equals(model.normalization(), template.getNormalization())
                && model.dimension() == VECTOR_DIMENSION
                && "l2".equals(model.normalization());
    }

    private List<List<Double>> parseVectors(String json) {
        try {
            List<List<Double>> vectors = objectMapper.readValue(json, new TypeReference<>() {});
            if (vectors == null || vectors.isEmpty() || vectors.size() > 20) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            for (List<Double> vector : vectors) {
                if (vector == null || vector.size() != VECTOR_DIMENSION
                        || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                    throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
                }
            }
            return vectors;
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
    }

    private void closeOwned(FaceSessionView session) {
        faceSessionStore.markInactive(session.id(), session.adminMemberId());
        aiFaceClient.deleteSession(session.id());
        faceSessionStore.delete(session.id());
    }

    private void closeOwnedQuietly(FaceSessionView session) {
        try {
            closeOwned(session);
        } catch (RuntimeException e) {
            log.warn("Face session cleanup failed: reason={}", e.getClass().getSimpleName());
        }
    }

    private void deleteAiSessionQuietly(UUID sessionId) {
        try {
            aiFaceClient.deleteSession(sessionId);
        } catch (RuntimeException e) {
            log.warn("Unpersisted face session cleanup failed: reason={}", e.getClass().getSimpleName());
        }
    }

    private static String normalizeFrameId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String id = value.trim();
        if (id.length() > 128 || id.indexOf('\r') >= 0 || id.indexOf('\n') >= 0) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return id;
    }

    private static MediaType frameMediaType(String value) {
        if (value == null) {
            throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        }
        String normalized = value.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (normalized) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/webp" -> MediaType.parseMediaType("image/webp");
            default -> throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
        };
    }
}
