package com.checkup.checkup.domain.volunteer.service;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
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

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;

    /**
     * 전체 학생의 봉사 횟수 명단을 이름·학번순으로 조회한다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(호실의 맨 앞자리). {@code null}이면 전체 층이고, 값이 있으면 호실 미배정 학생은 빠진다.
     * @param query    검색어. 숫자면 호실 번호나 학번이 정확히 같은 학생, 그 밖에는 이름에 포함된 학생. 비어 있으면 전체다.
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<VolunteerResponse> getVolunteers(Long memberId, Integer floor, String query) {
        adminVerifier.verify(memberId);
        return studentRepository.findAllByOrderByMember_NameAscStudentNumberAsc()
                .stream()
                .filter(student -> floor == null || Objects.equals(student.getDormitoryFloor(), floor))
                .filter(matches(query))
                .map(VolunteerResponse::from)
                .toList();
    }

    private static Predicate<Student> matches(String query) {
        if (query == null || query.isBlank()) {
            return student -> true;
        }
        String keyword = query.strip();
        if (DIGITS.matcher(keyword).matches()) {
            int number = Integer.parseInt(keyword);
            return student -> Objects.equals(student.getDormitoryRoom(), number)
                    || student.getStudentNumber() == number;
        }
        return student -> student.getMember().getName().contains(keyword);
    }
}
