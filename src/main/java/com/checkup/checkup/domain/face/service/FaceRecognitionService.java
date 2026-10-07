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
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
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
    private final FaceRecognitionLogService faceRecognitionLogService;
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
            FaceRecognitionLogService faceRecognitionLogService,
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
        this.faceRecognitionLogService = faceRecognitionLogService;
        this.adminVerifier = adminVerifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.framePermits = new Semaphore(properties.maxConcurrentFrames());
    }

    /**
     * 관리자 카메라 페이지의 얼굴 인식 세션을 만든다. 출석 대상(필수 동의·호실)이고 얼굴을 등록한 학생이 후보다.
     *
     * @param adminMemberId 세션의 관리자 회원 id
     * @param purpose       출석 용도
     * @return 새 세션 id와 용도
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         후보 학생이 없으면 {@link ErrorCode#FACE_NO_ENROLLED_STUDENTS}(409),
     *                         후보가 200명을 넘으면 {@link ErrorCode#FACE_TOO_MANY_CANDIDATES}(422),
     *                         저장된 템플릿의 모델이 서로 다르거나 벡터가 깨졌으면 {@link ErrorCode#FACE_AI_BAD_GATEWAY}(502).
     *                         AI 서버 오류는 {@link com.checkup.checkup.domain.face.ai.AiFaceException}으로 던지고
     *                         {@code GlobalExceptionHandler}가 {@link ErrorCode#FACE_AI_UNAVAILABLE}(503),
     *                         {@link ErrorCode#FACE_AI_TIMEOUT}(504), {@link ErrorCode#FACE_AI_BAD_GATEWAY}(502) 등으로 바꾼다
     */
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
                now, now.minus(properties.minFrameInterval()));
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

    /**
     * 카메라 프레임 한 장에서 얼굴을 인식하고, 현재 세션 후보로 확인된 학생을 출석 처리한다.
     * AI가 후보 밖의 학생을 지목하면 출석으로 인정하지 않고 {@code UNKNOWN}으로 낮춘다. 결과는 최근 인식 기록에도 남긴다(#143).
     * 프레임 이미지는 처리 뒤 메모리에서 지우고 저장하지 않는다.
     *
     * @param frameIdHeader 프레임 id(선택, 최대 128자). 없으면 새로 만든다
     * @param contentType   {@code image/jpeg} 또는 {@code image/webp}
     * @param image         프레임 이미지. 처리 뒤 0으로 덮어쓴다
     * @return 얼굴별 위치·품질·인식·출석 결과
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         세션이 없거나 닫혔으면 {@link ErrorCode#FACE_SESSION_NOT_FOUND}(404),
     *                         프레임 간격이 너무 짧으면 {@link ErrorCode#FACE_FRAME_RATE_LIMITED}(429),
     *                         동시 처리 한도를 넘으면 {@link ErrorCode#FACE_SERVICE_BUSY}(429),
     *                         이미지가 없거나 형식이 다르면 {@link ErrorCode#FACE_INVALID_MEDIA}(400),
     *                         이미지가 너무 크면 {@link ErrorCode#FACE_UPLOAD_TOO_LARGE}(413),
     *                         프레임 id가 128자를 넘거나 줄바꿈이 있으면 {@link ErrorCode#INVALID_REQUEST}(400),
     *                         AI 응답이 계약과 다르면 {@link ErrorCode#FACE_AI_BAD_GATEWAY}(502).
     *                         AI 서버 오류는 {@link com.checkup.checkup.domain.face.ai.AiFaceException}으로 던지고
     *                         {@code GlobalExceptionHandler}가 {@link ErrorCode#FACE_AI_UNAVAILABLE}(503),
     *                         {@link ErrorCode#FACE_AI_TIMEOUT}(504), {@link ErrorCode#FACE_AI_BAD_GATEWAY}(502) 등으로 바꾼다
     */
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
        UUID frameLockToken = UUID.randomUUID();
        boolean frameClaimed = false;
        try {
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
            Instant lockUntil = now.plus(properties.connectTimeout())
                    .plus(properties.responseTimeout().multipliedBy(2)).plusSeconds(30);
            frameClaimed = faceSessionStore.claimFrame(sessionId, adminMemberId, now,
                    now.minus(properties.minFrameInterval()), frameLockToken, lockUntil);
            if (!frameClaimed) {
                throw new CustomException(ErrorCode.FACE_FRAME_RATE_LIMITED);
            }

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
            return new FaceFrameResponse(frameId, result.faces().stream()
                    .map(face -> toPublicFace(currentSession, face, now)).toList());
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
            if (frameClaimed) {
                try {
                    faceSessionStore.releaseFrame(sessionId, adminMemberId, frameLockToken, clock.instant());
                } catch (RuntimeException e) {
                    // DB에 닿지 못해 잠금을 풀지 못해도, 설정한 AI 요청 시간이 지나면 잠금이 저절로 풀린다.
                    log.warn("Face frame lock release failed: reason={}", e.getClass().getSimpleName());
                }
            }
            framePermits.release();
            if (image != null) {
                Arrays.fill(image, (byte) 0);
            }
        }
    }

    /** 브라우저의 세션 종료. 여러 번 불러도 같다. DB 매핑이 남아 있으면 AI 쪽 삭제를 다시 시도한다. */
    public void close(Long adminMemberId, UUID sessionId) {
        adminVerifier.verify(adminMemberId);
        faceSessionStore.findOwnedIfPresent(sessionId, adminMemberId).ifPresent(this::closeOwned);
    }

    /** 관리자 한 명의 모든 세션을 닫는다. 로그아웃 때 부른다. 일부 세션 정리에 실패해도 나머지는 계속 닫는다. */
    public void closeAll(Long adminMemberId) {
        faceSessionStore.findForAdmin(adminMemberId).forEach(this::closeOwnedQuietly);
    }

    /** 학생이 후보에 들어 있는 세션을 모두 닫는다. 졸업·자퇴로 출석 대상에서 빠졌을 때 부른다. */
    public void removeStudent(Long studentId) {
        faceSessionStore.findWithStudent(studentId).forEach(this::closeOwnedQuietly);
    }

    /**
     * 유휴 시간이 지났고 처리 중인 프레임이 없는 세션을 닫는다. 정리 작업이 주기적으로 부른다.
     * AI 쪽 삭제가 실패해 비활성 상태로 남은 세션도 여기서 다시 정리한다.
     */
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
                switch (attendance) {
                    case "RECORDED" -> recordQuietly(session, face.trackId(), FaceRecognitionResult.SUCCESS,
                            student.get().getId(), verifiedAt);
                    case "STALE", "REJECTED" -> recordQuietly(session, face.trackId(), FaceRecognitionResult.FAILED,
                            student.get().getId(), verifiedAt);
                    default -> {
                        // 이미 출석한 학생(DUPLICATE)은 최근 인식에 남기지 않는다.
                    }
                }
            } else {
                // Spring이 관리하는 현재 세션 후보 목록 밖의 학생을 AI가 지목해도 절대 출석으로 인정하지 않는다.
                status = "UNKNOWN";
            }
        }
        if (attendance == null && face.qrRecommended()) {
            // 못 알아본 얼굴은 AI가 QR을 안내할 때만 한 줄 남기고, 다른 학생의 이름을 만들지 않는다.
            recordQuietly(session, face.trackId(), FaceRecognitionResult.FAILED, null, verifiedAt);
        }

        AiFaceFrameResponse.Quality quality = face.quality();
        return new FaceFrameResponse.Face(face.trackId(), face.bbox(), face.landmarks(),
                new FaceFrameResponse.Quality(quality.brightness(), quality.sharpness(), quality.issues()),
                new FaceFrameResponse.Recognition(status, studentName, studentNumber, attendance),
                face.attempts(), face.qrRecommended());
    }

    /**
     * 최근 인식 기록을 남긴다(#143). 기록은 화면 편의용이라 실패해도 인식·출석 응답은 그대로 돌려준다.
     */
    private void recordQuietly(FaceSessionView session, String trackId, FaceRecognitionResult result,
                               Long studentId, Instant recognizedAt) {
        try {
            faceRecognitionLogService.record(session, trackId, result, studentId, recognizedAt);
        } catch (RuntimeException e) {
            log.warn("Face recognition log failed: reason={}", e.getClass().getSimpleName());
        }
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
