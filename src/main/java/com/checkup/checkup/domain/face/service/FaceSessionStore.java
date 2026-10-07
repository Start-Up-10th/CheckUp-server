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

    @Transactional
    public void create(UUID sessionId, Long adminMemberId, AttendancePurpose purpose, List<Long> studentIds,
                       Instant createdAt) {
        FaceRecognitionSession session = sessionRepository.save(
                FaceRecognitionSession.create(sessionId, adminMemberId, purpose, createdAt));
        List<Student> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new CustomException(ErrorCode.FACE_NO_ENROLLED_STUDENTS);
        }
        candidateRepository.saveAll(students.stream()
                .map(student -> FaceRecognitionSessionCandidate.create(session, student))
                .toList());
    }

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

    @Transactional(readOnly = true)
    public Optional<FaceSessionView> findOwnedIfPresent(UUID sessionId, Long adminMemberId) {
        return sessionRepository.findByIdAndAdminMemberId(sessionId, adminMemberId)
                .map(session -> view(session, candidateRepository.findAllBySession_Id(sessionId)));
    }

    @Transactional
    public boolean claimFrame(UUID sessionId, Long adminMemberId, Instant now, Instant cutoff,
                              UUID token, Instant lockUntil) {
        return sessionRepository.claimFrame(sessionId, adminMemberId, now, cutoff, token, lockUntil) == 1;
    }

    @Transactional
    public boolean extendFrame(UUID sessionId, Long adminMemberId, UUID token, Instant now, Instant lockUntil) {
        return sessionRepository.extendFrame(sessionId, adminMemberId, token, now, lockUntil) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseFrame(UUID sessionId, Long adminMemberId, UUID token, Instant now) {
        sessionRepository.releaseFrame(sessionId, adminMemberId, token, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInactive(UUID sessionId, Long adminMemberId) {
        sessionRepository.markInactive(sessionId, adminMemberId);
    }

    @Transactional
    public boolean markInactiveIfIdle(UUID sessionId, Instant cutoff, Instant now) {
        return sessionRepository.markInactiveIfIdle(sessionId, cutoff, now) == 1;
    }

    @Transactional(readOnly = true)
    public List<FaceSessionView> findForAdmin(Long adminMemberId) {
        return sessionRepository.findAllByAdminMemberId(adminMemberId).stream()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FaceSessionView> findWithStudent(Long studentId) {
        return candidateRepository.findAllByStudent_Id(studentId).stream()
                .map(candidate -> candidate.getSession())
                .distinct()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FaceSessionView> findIdleBefore(Instant cutoff, Instant now) {
        return sessionRepository.findIdleBefore(cutoff, now).stream()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

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
