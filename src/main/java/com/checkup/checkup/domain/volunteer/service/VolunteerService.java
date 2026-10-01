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
 */
@Service
@RequiredArgsConstructor
public class VolunteerService {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");

    /** 호실 → 이름 → 학번순. 호실이 없는 학생은 맨 뒤에 둔다. */
    private static final Comparator<Student> ROOM_ORDER = Comparator
            .comparing(Student::getDormitoryRoom, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(student -> student.getMember().getName())
            .thenComparing(Student::getStudentNumber);

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;
    private final VolunteerAdjustmentRepository volunteerAdjustmentRepository;

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
