package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.dto.FaceRecognitionLogResponse;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.face.repository.FaceRecognitionLogRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 얼굴 출석 화면의 최근 인식 기록을 남기고 조회한다(#143, REQ-ATT-007).
 *
 * 조회는 그 관리자가 연 세션의 오늘(운영일) 기록만 보여준다. 지난 운영일 기록은 08:00 KST에 지운다.
 */
@Service
@RequiredArgsConstructor
public class FaceRecognitionLogService {

    private static final int MAX_TRACK_ID_LENGTH = 128;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final FaceRecognitionLogRepository faceRecognitionLogRepository;
    private final AdminVerifier adminVerifier;
    private final OperatingDayCalculator operatingDayCalculator;

    /**
     * 인식 결과 한 줄을 남긴다. 같은 세션·같은 얼굴·같은 결과는 한 줄만 남는다.
     * AI가 보낸 trackId가 128자를 넘으면 남기지 않는다.
     *
     * @param studentId 알아본 학생 DB id. 못 알아봤으면 {@code null}
     */
    @Transactional
    public void record(FaceSessionView session, String trackId, FaceRecognitionResult result,
                       Long studentId, Instant recognizedAt) {
        if (trackId.length() > MAX_TRACK_ID_LENGTH) {
            return;
        }
        faceRecognitionLogRepository.insertIfAbsent(
                session.id(), session.adminMemberId(), session.purpose().name(),
                operatingDayCalculator.of(recognizedAt), trackId, result.name(), studentId, recognizedAt);
    }

    /**
     * 관리자가 연 세션의 오늘(운영일) 한 용도 기록을 최신순으로 돌려준다.
     *
     * @param limit 최대 개수(선택, 1~100). 없으면 50
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         개수가 범위를 벗어나면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional(readOnly = true)
    public List<FaceRecognitionLogResponse> getRecent(Long adminMemberId, AttendancePurpose purpose, Integer limit) {
        adminVerifier.verify(adminMemberId);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return faceRecognitionLogRepository
                .findByAdminMemberIdAndOperatingDayAndPurposeOrderByRecognizedAtDescIdDesc(
                        adminMemberId, operatingDayCalculator.today(), purpose, Limit.of(size))
                .stream()
                .map(FaceRecognitionLogResponse::from)
                .toList();
    }

    /**
     * 지난 운영일 기록을 지운다.
     *
     * @return 지운 기록 수
     */
    @Transactional
    public int deleteExpired() {
        return faceRecognitionLogRepository.deleteByOperatingDayBefore(operatingDayCalculator.today());
    }
}
