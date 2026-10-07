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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

/** AI 세션을 시작하고, 카메라 프레임 속도를 제한하며, 출석 전에 얼굴 일치 결과를 검증한다. */
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
    private final MeterRegistry meterRegistry;
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
            Clock clock,
            MeterRegistry meterRegistry
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
        this.meterRegistry = meterRegistry;
        this.framePermits = new Semaphore(properties.maxConcurrentFrames());
    }

    public FaceSessionResponse create(Long adminMemberId, AttendancePurpose purpose) {
        adminVerifier.verify(adminMemberId);
        List<FaceTemplate> templates = eligibleTemplates();
        if (templates.isEmpty()) {
            throw new CustomException(ErrorCode.FACE_NO_ENROLLED_STUDENTS);
        }
        if (templates.size() > MAX_CANDIDATES) {
            throw new CustomException(ErrorCode.FACE_TOO_MANY_CANDIDATES);
        }
        AiFaceSessionRequest request = sessionRequest(templates);
        UUID sessionId = UUID.randomUUID();
        Instant now = clock.instant();
        // AI를 부르기 전에 세션 소유 정보를 먼저 저장한다. 그래야 AI 쪽 정리가 실패해도 다시 시도할 수 있다.
        faceSessionStore.create(sessionId, adminMemberId, purpose,
                templates.stream().map(template -> template.getStudent().getId()).toList(),
                now);
        try {
            aiFaceClient.createSession(sessionId, request);
        } catch (RuntimeException e) {
            try {
                faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
            } catch (RuntimeException cleanupLookupFailure) {
                log.warn("Unready face session cleanup deferred: reason={}",
                        cleanupLookupFailure.getClass().getSimpleName());
            }
            throw e;
        }
        return new FaceSessionResponse(sessionId, purpose, "ACTIVE");
    }

    public FaceFrameResponse recognize(
            Long adminMemberId,
            UUID sessionId,
            String frameIdHeader,
            String contentType,
            byte[] image
    ) {
        Timer.Sample totalTiming = startTiming();
        Timer.Sample stageTiming = startTiming();
        String stage = "pre_ai";
        String stageOutcome = "error";
        String outcome = "error";
        UUID frameLockToken = UUID.randomUUID();
        boolean frameClaimed = false;
        boolean permitAcquired = false;
        try {
            adminVerifier.verify(adminMemberId);
            if (image == null || image.length == 0) {
                throw new CustomException(ErrorCode.FACE_INVALID_MEDIA);
            }
            if (image.length > properties.maxFrameBytes()) {
                throw new CustomException(ErrorCode.FACE_UPLOAD_TOO_LARGE);
            }
            MediaType mediaType = frameMediaType(contentType);
            String frameId = normalizeFrameId(frameIdHeader);
            permitAcquired = framePermits.tryAcquire();
            if (!permitAcquired) {
                throw new CustomException(ErrorCode.FACE_SERVICE_BUSY);
            }
            FaceSessionView session = faceSessionStore.findOwned(sessionId, adminMemberId);
            Instant now = clock.instant();
            if (!session.lastActivityAt().isAfter(now.minus(properties.sessionIdleTimeout()))) {
                closeOwned(session);
                throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
            }
            // 후보 중 저장되지 않았거나 호실이 빠진 학생이 있으면 세션을 닫는다. 후보 수만큼 쿼리하지 않고 한 번에 센다.
            Set<Long> candidates = session.candidateStudentIds();
            if (studentRepository.countByIdInAndDormitoryRoomIsNotNull(candidates) != candidates.size()) {
                closeOwned(session);
                throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
            }
            Instant frameStartedAt = clock.instant();
            Instant lockUntil = frameStartedAt.plus(properties.connectTimeout())
                    .plus(properties.responseTimeout().multipliedBy(2)).plusSeconds(30);
            frameClaimed = faceSessionStore.claimFrame(sessionId, adminMemberId, frameStartedAt,
                    frameStartedAt.minus(properties.minFrameInterval()), frameLockToken, lockUntil);
            if (!frameClaimed) {
                throw new CustomException(ErrorCode.FACE_FRAME_RATE_LIMITED);
            }
            recordTiming(stageTiming, stage, "success");
            stage = "ai";
            stageTiming = startTiming();

            AiFaceFrameResponse result;
            try {
                result = aiFaceClient.recognize(sessionId, frameId, image, mediaType);
            } catch (AiFaceException e) {
                if (e.getStatus() != 404 || !"frame".equals(e.getOperation())) {
                    throw e;
                }
                // 계약상 404는 AI 세션이 없다는 뜻이라 이 프레임은 처리되지 않았다. 세션을 다시 만들고 한 번만 재시도한다.
                try {
                    Instant recoveryUntil = clock.instant().plus(properties.frameRecoveryLease());
                    if (!faceSessionStore.extendFrame(sessionId, adminMemberId, frameLockToken,
                            clock.instant(), recoveryUntil)) {
                        throw new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND);
                    }
                    aiFaceClient.createSession(sessionId, candidateRequest(session.candidateStudentIds()));
                    result = aiFaceClient.recognize(sessionId, frameId, image, mediaType);
                } catch (RuntimeException recoveryFailure) {
                    faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
                    throw recoveryFailure;
                }
            }
            recordTiming(stageTiming, stage, "success");
            stage = "result";
            stageTiming = startTiming();
            if (result == null || !frameId.equals(result.frameId()) || result.faces() == null) {
                throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
            }
            result.faces().forEach(FaceRecognitionService::validateFace);
            FaceSessionView currentSession;
            try {
                currentSession = faceSessionStore.findOwned(sessionId, adminMemberId);
            } catch (CustomException closed) {
                deleteAiSessionQuietly(sessionId);
                throw closed;
            }
            FaceFrameResponse response = new FaceFrameResponse(frameId, result.faces().stream()
                    .map(face -> toPublicFace(currentSession, face, now)).toList());
            stageOutcome = "success";
            outcome = "success";
            return response;
        } catch (AiFaceException e) {
            if (e.getStatus() >= 500 || e.getStatus() == 404) {
                faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
            }
            throw e;
        } catch (CustomException e) {
            outcome = switch (e.getErrorCode()) {
                case FACE_SERVICE_BUSY -> "busy";
                case FACE_FRAME_RATE_LIMITED -> "rate_limited";
                default -> "error";
            };
            if (e.getErrorCode() == ErrorCode.FACE_AI_BAD_GATEWAY) {
                faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwnedQuietly);
            }
            throw e;
        } finally {
            recordTiming(stageTiming, stage, stageOutcome);
            if (frameClaimed) {
                Timer.Sample releaseTiming = startTiming();
                String releaseOutcome = "error";
                try {
                    faceSessionStore.releaseFrame(sessionId, adminMemberId, frameLockToken, clock.instant());
                    releaseOutcome = "success";
                } catch (RuntimeException e) {
                    // DB에 닿지 못해 잠금을 풀지 못해도, 설정한 AI 요청 시간이 지나면 잠금이 저절로 풀린다.
                    log.warn("Face frame lock release failed: reason={}", e.getClass().getSimpleName());
                } finally {
                    recordTiming(releaseTiming, "release", releaseOutcome);
                }
            }
            if (permitAcquired) {
                framePermits.release();
            }
            if (image != null) {
                Arrays.fill(image, (byte) 0);
            }
            recordTiming(totalTiming, "total", outcome);
        }
    }

    private Timer.Sample startTiming() {
        try {
            return Timer.start(meterRegistry);
        } catch (RuntimeException e) {
            log.warn("Face frame timing unavailable: reason={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void recordTiming(Timer.Sample sample, String stage, String outcome) {
        if (sample == null) {
            return;
        }
        try {
            long elapsedNanos = sample.stop(Timer.builder("checkup.face.frame.duration")
                    .tags("stage", stage, "outcome", outcome)
                    .register(meterRegistry));
            log.debug("Face frame timing: stage={}, outcome={}, durationMs={}",
                    stage, outcome, elapsedNanos / 1_000_000.0);
        } catch (RuntimeException e) {
            // 계측 장애가 출석 결과, 프레임 잠금 해제 또는 원본 폐기를 막지 않게 한다.
            log.warn("Face frame timing unavailable: reason={}", e.getClass().getSimpleName());
        }
    }

    /** 브라우저의 세션 종료. 여러 번 불러도 같다. DB 매핑이 남아 있으면 AI 쪽 삭제를 다시 시도한다. */
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
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.sessionIdleTimeout());
        faceSessionStore.findIdleBefore(cutoff, now).forEach(session -> {
            if (!session.active() || faceSessionStore.markInactiveIfIdle(session.id(), cutoff, now)) {
                closeOwnedQuietly(session);
            }
        });
    }

    private FaceFrameResponse.Face toPublicFace(
            FaceSessionView session,
            AiFaceFrameResponse.FaceResult face,
            Instant verifiedAt
    ) {
        validateFace(face);
        String status = face.recognition().status();

        String studentName = null;
        Integer studentNumber = null;
        String attendance = null;
        if ("KNOWN".equals(status)) {
            Long dataGsmStudentId = canonicalStudentId(face.recognition().studentId());
            var student = dataGsmStudentId == null ? java.util.Optional.<Student>empty()
                    : studentRepository.findByDatagsmStudentId(dataGsmStudentId);
            if (student.isPresent()
                    && session.candidateStudentIds().contains(student.get().getId())
                    && student.get().isAttendanceEligible()) {
                studentName = student.get().getName();
                studentNumber = student.get().getStudentNumber();
                AttendanceRecordResult recorded = attendanceService.markAttended(
                        student.get().getId(), session.purpose(), verifiedAt, AttendanceMethod.FACE);
                attendance = attendanceResult(recorded);
            } else {
                // Spring이 관리하는 현재 세션 후보 목록 밖의 학생을 AI가 지목해도 절대 출석으로 인정하지 않는다.
                status = "UNKNOWN";
            }
        }

        AiFaceFrameResponse.Quality quality = face.quality();
        return new FaceFrameResponse.Face(face.trackId(), face.bbox(), face.landmarks(),
                new FaceFrameResponse.Quality(quality.brightness(), quality.sharpness(), quality.issues()),
                new FaceFrameResponse.Recognition(status, studentName, studentNumber, attendance),
                face.attempts(), face.qrRecommended());
    }

    private static void validateFace(AiFaceFrameResponse.FaceResult face) {
        if (face == null || face.trackId() == null || face.quality() == null || face.recognition() == null) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
        String status = face.recognition().status();
        List<Double> bbox = face.bbox();
        if (status == null || !Set.of("KNOWN", "UNKNOWN", "NOT_ATTEMPTED").contains(status)
                || bbox == null || bbox.size() != 4
                || face.trackId().isBlank()
                || bbox.stream().anyMatch(value -> value == null || !Double.isFinite(value)
                    || value < 0.0 || value > 1.0)
                || bbox.get(0) + bbox.get(2) > 1.000001
                || bbox.get(1) + bbox.get(3) > 1.000001
                || face.landmarks() == null
                || face.landmarks().stream().anyMatch(point -> point == null || point.size() != 2
                    || point.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0.0))
                || qualityInvalid(face.quality()) || face.attempts() < 0
                || notFinite(face.recognition().score()) || notFinite(face.recognition().margin())) {
            throw new CustomException(ErrorCode.FACE_AI_BAD_GATEWAY);
        }
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
            Long parsed = Long.parseLong(value);
            return parsed > 0 && parsed.toString().equals(value) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<FaceTemplate> eligibleTemplates() {
        return faceTemplateRepository.findAllByOrderByStudent_IdAsc().stream()
                .filter(template -> eligibleStudent(template.getStudent()))
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

    private static boolean eligibleStudent(Student student) {
        return student.isAttendanceEligible()
                && student.getDatagsmStudentId() != null && student.getDatagsmStudentId() > 0;
    }

    private AiFaceSessionRequest candidateRequest(Set<Long> studentIds) {
        List<FaceTemplate> templates = faceTemplateRepository.findAllByStudent_IdIn(studentIds).stream()
                .filter(template -> eligibleStudent(template.getStudent()))
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
                double normSquared = vector.stream().mapToDouble(value -> value * value).sum();
                if (Math.abs(Math.sqrt(normSquared) - 1.0) > 0.01) {
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
