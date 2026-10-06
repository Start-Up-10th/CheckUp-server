package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.entity.FaceRecognitionSession;
import com.checkup.checkup.domain.face.entity.FaceRecognitionSessionCandidate;
import com.checkup.checkup.domain.face.repository.FaceRecognitionSessionCandidateRepository;
import com.checkup.checkup.domain.face.repository.FaceRecognitionSessionRepository;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 인식 세션 소유 정보와 후보 목록을 다루는 짧은 DB 전용 트랜잭션. */
@Service
@RequiredArgsConstructor
public class FaceSessionStore {
    private final FaceRecognitionSessionRepository sessionRepository;
    private final FaceRecognitionSessionCandidateRepository candidateRepository;
    private final StudentRepository studentRepository;

    /**
     * 관리자 얼굴 인식 세션과 후보 학생 목록을 저장한다. AI 세션을 만들기 전에 먼저 저장해, AI 쪽 정리가 실패해도 다시 시도할 수 있게 한다.
     *
     * @param initialLastActivityAt 첫 프레임을 바로 받을 수 있도록 최소 프레임 간격만큼 앞당긴 마지막 활동 시각
     * @throws CustomException 후보 학생 중 저장되지 않은 학생이 있으면 {@link ErrorCode#FACE_NO_ENROLLED_STUDENTS}
     */
    @Transactional
    public void create(UUID sessionId, Long adminMemberId, AttendancePurpose purpose, List<Long> studentIds,
                       Instant createdAt, Instant initialLastActivityAt) {
        FaceRecognitionSession session = sessionRepository.save(
                FaceRecognitionSession.create(sessionId, adminMemberId, purpose, createdAt, initialLastActivityAt));
        List<Student> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new CustomException(ErrorCode.FACE_NO_ENROLLED_STUDENTS);
        }
        candidateRepository.saveAll(students.stream()
                .map(student -> FaceRecognitionSessionCandidate.create(session, student))
                .toList());
    }

    /**
     * 관리자 본인의 활성 세션을 후보 학생 id와 함께 읽는다.
     *
     * @throws CustomException 세션이 없거나, 다른 관리자의 세션이거나, 이미 닫혔으면 {@link ErrorCode#FACE_SESSION_NOT_FOUND}(404)
     */
    @Transactional(readOnly = true)
    public FaceSessionView findOwned(UUID sessionId, Long adminMemberId) {
        FaceRecognitionSession session = sessionRepository.findByIdAndAdminMemberIdAndActiveTrue(sessionId, adminMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.FACE_SESSION_NOT_FOUND));
        Set<Long> candidateIds = candidateRepository.findAllBySession_Id(sessionId).stream()
                .map(candidate -> candidate.getStudent().getId())
                .collect(Collectors.toUnmodifiableSet());
        return new FaceSessionView(session.getId(), session.getAdminMemberId(), session.getPurpose(),
                session.getLastActivityAt(), session.isActive(), candidateIds);
    }

    /**
     * 관리자 본인의 세션을 활성 여부와 상관없이 읽는다. 닫는 중인 세션의 정리를 다시 시도할 때 쓴다.
     *
     * @return 없거나 다른 관리자의 세션이면 비어 있다
     */
    @Transactional(readOnly = true)
    public Optional<FaceSessionView> findOwnedIfPresent(UUID sessionId, Long adminMemberId) {
        return sessionRepository.findByIdAndAdminMemberId(sessionId, adminMemberId)
                .map(session -> view(session, candidateRepository.findAllBySession_Id(sessionId)));
    }

    /**
     * 프레임 처리 잠금을 잡는다. 최소 프레임 간격이 지났고 다른 프레임이 처리 중이 아닐 때만 잡힌다.
     *
     * @param cutoff    이 시각 이전에 마지막으로 활동했어야 한다(프레임 간격 제한)
     * @param token     이 프레임의 잠금 토큰
     * @param lockUntil AI 응답이 오지 않아도 잠금이 저절로 풀리는 시각
     * @return 잠금을 잡았으면 {@code true}
     */
    @Transactional
    public boolean claimFrame(UUID sessionId, Long adminMemberId, Instant now, Instant cutoff,
                              UUID token, Instant lockUntil) {
        return sessionRepository.claimFrame(sessionId, adminMemberId, now, cutoff, token, lockUntil) == 1;
    }

    /**
     * AI 세션을 다시 만드는 동안 이 프레임의 잠금 시간을 늘린다. 잠금이 아직 유효하고 토큰이 같을 때만 늘린다.
     *
     * @return 늘렸으면 {@code true}
     */
    @Transactional
    public boolean extendFrame(UUID sessionId, Long adminMemberId, UUID token, Instant now, Instant lockUntil) {
        return sessionRepository.extendFrame(sessionId, adminMemberId, token, now, lockUntil) == 1;
    }

    /**
     * 이 프레임의 잠금을 풀고 마지막 활동 시각을 갱신한다. 호출한 쪽 트랜잭션이 실패해도 풀리도록 새 트랜잭션에서 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseFrame(UUID sessionId, Long adminMemberId, UUID token, Instant now) {
        sessionRepository.releaseFrame(sessionId, adminMemberId, token, now);
    }

    /**
     * 관리자 본인의 세션을 비활성으로 바꾸고 잠금을 푼다. 닫기 도중 실패해도 새 프레임을 받지 않도록 새 트랜잭션에서 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInactive(UUID sessionId, Long adminMemberId) {
        sessionRepository.markInactive(sessionId, adminMemberId);
    }

    /**
     * 유휴 시간이 지났고 처리 중인 프레임이 없을 때만 세션을 비활성으로 바꾼다. 정리 작업이 처리 중인 프레임을 끊지 않게 한다.
     *
     * @return 비활성으로 바꿨으면 {@code true}
     */
    @Transactional
    public boolean markInactiveIfIdle(UUID sessionId, Instant cutoff, Instant now) {
        return sessionRepository.markInactiveIfIdle(sessionId, cutoff, now) == 1;
    }

    /** 관리자 한 명의 모든 세션을 읽는다. 로그아웃 때 그 관리자의 세션을 모두 닫을 때 쓴다. */
    @Transactional(readOnly = true)
    public List<FaceSessionView> findForAdmin(Long adminMemberId) {
        return sessionRepository.findAllByAdminMemberId(adminMemberId).stream()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    /** 학생이 후보에 들어 있는 세션을 읽는다. 학생이 졸업·자퇴했을 때 그 세션을 닫을 때 쓴다. */
    @Transactional(readOnly = true)
    public List<FaceSessionView> findWithStudent(Long studentId) {
        return candidateRepository.findAllByStudent_Id(studentId).stream()
                .map(candidate -> candidate.getSession())
                .distinct()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    /** 이미 비활성이거나, 유휴 시간이 지났고 처리 중인 프레임이 없는 세션을 읽는다. 정리 작업이 쓴다. */
    @Transactional(readOnly = true)
    public List<FaceSessionView> findIdleBefore(Instant cutoff, Instant now) {
        return sessionRepository.findIdleBefore(cutoff, now).stream()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    /** 세션과 후보 목록을 지운다. 이미 없으면 아무것도 하지 않는다. AI 세션을 지운 뒤 호출한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID sessionId) {
        if (sessionRepository.existsById(sessionId)) {
            candidateRepository.deleteAllForSession(sessionId);
            sessionRepository.deleteById(sessionId);
        }
    }

    private static FaceSessionView view(FaceRecognitionSession session,
                                        List<FaceRecognitionSessionCandidate> candidates) {
        Set<Long> ids = candidates.stream().map(candidate -> candidate.getStudent().getId())
                .collect(Collectors.toUnmodifiableSet());
        return new FaceSessionView(session.getId(), session.getAdminMemberId(), session.getPurpose(),
                session.getLastActivityAt(), session.isActive(), ids);
    }
}
