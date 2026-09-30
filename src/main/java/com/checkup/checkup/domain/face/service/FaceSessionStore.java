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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Short DB-only transactions for Spring's recognition-session ownership and target list. */
@Service
@RequiredArgsConstructor
public class FaceSessionStore {
    private final FaceRecognitionSessionRepository sessionRepository;
    private final FaceRecognitionSessionCandidateRepository candidateRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void create(UUID sessionId, Long adminMemberId, AttendancePurpose purpose, List<Long> studentIds,
                       Instant createdAt, Instant initialLastActivityAt) {
        FaceRecognitionSession session = sessionRepository.save(
                FaceRecognitionSession.create(sessionId, adminMemberId, purpose, createdAt, initialLastActivityAt));
        List<Student> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new CustomException(ErrorCode.FACE_NO_CANDIDATES);
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
    public boolean claimFrame(UUID sessionId, Long adminMemberId, Instant now, Instant cutoff) {
        return sessionRepository.claimFrame(sessionId, adminMemberId, now, cutoff) == 1;
    }

    @Transactional
    public void markInactive(UUID sessionId, Long adminMemberId) {
        sessionRepository.markInactive(sessionId, adminMemberId);
    }

    @Transactional
    public boolean markInactiveIfIdle(UUID sessionId, Instant cutoff) {
        return sessionRepository.markInactiveIfIdle(sessionId, cutoff) == 1;
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
    public List<FaceSessionView> findIdleBefore(Instant cutoff) {
        return sessionRepository.findIdleBefore(cutoff).stream()
                .map(session -> view(session, candidateRepository.findAllBySession_Id(session.getId())))
                .toList();
    }

    @Transactional
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
