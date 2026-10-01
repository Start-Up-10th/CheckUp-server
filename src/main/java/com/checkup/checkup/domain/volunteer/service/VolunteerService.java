package com.checkup.checkup.domain.volunteer.service;

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

/**
 * 봉사 관리(DEC-020). 사감·기숙사 자치위원(관리자)만 사용할 수 있다.
 *
 * 명단은 저장된 전체 학생이다. 관리자는 이 명단에서 학생의 봉사 횟수를 조정한다.
 */
@Service
@RequiredArgsConstructor
public class VolunteerService {

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;

    /**
     * 전체 학생의 봉사 횟수 명단을 이름·학번순으로 조회한다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(호실의 맨 앞자리). {@code null}이면 전체 층이고, 값이 있으면 호실 미배정 학생은 빠진다.
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<VolunteerResponse> getVolunteers(Long memberId, Integer floor) {
        adminVerifier.verify(memberId);
        return studentRepository.findAllByOrderByMember_NameAscStudentNumberAsc()
                .stream()
                .filter(student -> floor == null || Objects.equals(student.getDormitoryFloor(), floor))
                .map(VolunteerResponse::from)
                .toList();
    }
}
