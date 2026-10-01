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
     * 전체 학생의 봉사 횟수 명단을 이름·학번순으로 조회한다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(412호면 4). 없으면 전체 층
     */
    @GetMapping
    public List<VolunteerResponse> getVolunteers(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Integer floor
    ) {
        return volunteerService.getVolunteers(memberId, floor);
    }
}
