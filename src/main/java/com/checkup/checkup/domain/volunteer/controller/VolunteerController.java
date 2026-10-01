package com.checkup.checkup.domain.volunteer.controller;

import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 봉사 관리 API(DEC-020). 사감·기숙사 자치위원이 전체 학생 명단을 보고 봉사 횟수를 조정한다.
 */
@RestController
@RequestMapping("/api/v1/volunteer")
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerService volunteerService;

    /**
     * 전체 학생의 봉사 횟수 명단을 호실·이름·학번순으로 조회한다. 웹은 같은 호실끼리 묶어 보여준다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(412호면 4). 없으면 전체 층
     * @param q        검색어. 숫자면 호실 번호·학번이 정확히 같은 학생, 그 밖에는 이름에 포함된 학생
     */
    @GetMapping
    public List<VolunteerResponse> getVolunteers(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String q
    ) {
        return volunteerService.getVolunteers(memberId, floor, q);
    }
}
