package com.checkup.checkup.domain.volunteer.service;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.repository.VolunteerAdjustmentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 봉사 관리(DEC-020). 사감·기숙사 자치위원(관리자)만 사용할 수 있다.
 *
 * 명단은 저장된 전체 학생이다. 관리자는 이 명단에서 학생의 봉사 횟수를 조정한다.
 * 봉사 횟수는 앞으로 해야 할 봉사 횟수이고, 조정은 알림을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VolunteerService {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final int MAX_REQUEST_KEY_LENGTH = 100;

    /** 호실 → 이름 → 학번순. 호실이 없는 학생은 맨 뒤에 둔다. */
    private static final Comparator<Student> ROOM_ORDER = Comparator
            .comparing(Student::getDormitoryRoom, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(student -> student.getMember().getName())
            .thenComparing(Student::getStudentNumber);

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;
    private final VolunteerAdjustmentRepository volunteerAdjustmentRepository;
    private final Clock clock;

    /**
     * 전체 학생의 봉사 횟수 명단을 호실·이름·학번순으로 조회한다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(호실의 맨 앞자리). {@code null}이면 전체 층이고, 값이 있으면 호실 미배정 학생은 빠진다.
     * @param query    검색어. 숫자면 호실 번호나 학번이 정확히 같은 학생이다. 그 밖에는 이름으로 찾고,
     *                 이름에 포함 → 초성 일치 → 오타 1개 순으로 앞에 둔다({@link KoreanNameMatcher}). 비어 있으면 전체다.
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<VolunteerResponse> getVolunteers(Long memberId, Integer floor, String query) {
        adminVerifier.verify(memberId);
        ToIntFunction<Student> rank = ranker(query);
        Map<Long, Instant> lastActivities = volunteerAdjustmentRepository.findLastActivities().stream()
                .collect(Collectors.toMap(
                        VolunteerAdjustmentRepository.LastActivity::getStudentId,
                        VolunteerAdjustmentRepository.LastActivity::getLastActivityAt));
        return studentRepository.findAllByOrderByMember_NameAscStudentNumberAsc()
                .stream()
                .filter(student -> floor == null || Objects.equals(student.getDormitoryFloor(), floor))
                .filter(student -> rank.applyAsInt(student) != KoreanNameMatcher.NO_MATCH)
                .sorted(Comparator.comparingInt(rank).thenComparing(ROOM_ORDER))
                .map(student -> VolunteerResponse.of(student, lastActivities.get(student.getId())))
                .toList();
    }

    /**
     * 봉사 횟수를 1 늘린다. 기숙사에서 잘못해 봉사가 생겼을 때 쓴다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @return 조정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         키가 100자를 넘으면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerResponse increase(Long memberId, Long studentId, String requestKey) {
        return adjust(memberId, studentId, requestKey, 1);
    }

    /**
     * 봉사 횟수를 1 줄인다. 봉사를 마쳤거나 사감이 특별한 사정으로 감면할 때 쓴다. 0 미만으로는 줄이지 않는다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @return 조정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         횟수가 0이면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409),
     *                         키가 100자를 넘으면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerResponse decrease(Long memberId, Long studentId, String requestKey) {
        return adjust(memberId, studentId, requestKey, -1);
    }

    /**
     * 조정 기록을 먼저 남기고 횟수를 바꾼다. 같은 키의 기록이 이미 있으면 재시도로 보고 횟수를 바꾸지 않는다.
     * 차감할 수 없으면 예외로 트랜잭션이 취소돼 기록도 남지 않는다.
     */
    private VolunteerResponse adjust(Long memberId, Long studentId, String requestKey, int delta) {
        adminVerifier.verify(memberId);
        Long id = studentRepository.findByDatagsmStudentId(studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND))
                .getId();
        String key = normalizeKey(requestKey);

        if (volunteerAdjustmentRepository.insertIfAbsent(id, delta, key, clock.instant()) == 1) {
            int changed = delta > 0
                    ? studentRepository.increaseVolunteerCount(id)
                    : studentRepository.decreaseVolunteerCount(id);
            if (changed == 0) {
                throw new CustomException(ErrorCode.VOLUNTEER_COUNT_ZERO);
            }
        }

        Student updated = studentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return VolunteerResponse.of(updated, volunteerAdjustmentRepository.findLastActivityAt(id));
    }

    private static String normalizeKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        String key = requestKey.strip();
        if (key.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return key;
    }

    /**
     * 검색어에 맞는 정도를 순위로 돌려주는 함수를 만든다. 순위가 낮을수록 앞에 오고, 맞지 않으면 {@link KoreanNameMatcher#NO_MATCH}다.
     */
    private static ToIntFunction<Student> ranker(String query) {
        if (query == null || query.isBlank()) {
            return student -> KoreanNameMatcher.CONTAINS;
        }
        String keyword = query.strip();
        if (DIGITS.matcher(keyword).matches()) {
            int number = Integer.parseInt(keyword);
            return student -> Objects.equals(student.getDormitoryRoom(), number) || student.getStudentNumber() == number
                    ? KoreanNameMatcher.CONTAINS
                    : KoreanNameMatcher.NO_MATCH;
        }
        return student -> KoreanNameMatcher.rank(student.getMember().getName(), keyword);
    }
}
