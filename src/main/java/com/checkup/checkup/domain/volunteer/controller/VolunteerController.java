package com.checkup.checkup.domain.volunteer.controller;

import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private final VolunteerService volunteerService;

    /**
     * 전체 학생의 봉사 횟수 명단을 호실·이름·학번순으로 조회한다. 웹은 같은 호실끼리 묶어 보여준다.
     *
     * @param memberId 세션의 회원 id
     * @param q        검색어. 숫자면 호실 번호·학번이 정확히 같은 학생, 그 밖에는 이름(포함·초성·오타 1개)
     * @param minCount 최소 봉사 횟수. 당일 봉사자 지정 후보는 {@code minCount=1}로 찾는다. 없으면 제한 없다.
     * @param onDuty   {@code true}면 오늘 당일 봉사자로 지정된 학생만 본다(자치위원의 완료 확인용).
     */
    @GetMapping
    public List<VolunteerResponse> getVolunteers(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minCount,
            @RequestParam(required = false) Boolean onDuty
    ) {
        return volunteerService.getVolunteers(memberId, q, minCount, onDuty);
    }

    /**
     * 봉사 횟수를 1 늘린다. 웹은 클릭마다 새 {@code Idempotency-Key}를 보내면 재시도가 중복 반영되지 않는다.
     *
     * @param memberId       세션의 회원 id
     * @param studentId      DataGSM 학생 id
     * @param idempotencyKey 재시도 방지 키(선택, 100자 이하)
     * @return 조정 뒤 학생의 명단 항목
     */
    @PatchMapping("/{studentId}/count/increase")
    public VolunteerResponse increase(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long studentId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return volunteerService.increase(memberId, studentId, idempotencyKey);
    }

    /**
     * 봉사 횟수를 1 줄인다. 봉사 완료나 사감의 감면에 쓴다. 횟수가 0이면 409다.
     *
     * @param memberId       세션의 회원 id
     * @param studentId      DataGSM 학생 id
     * @param idempotencyKey 재시도 방지 키(선택, 100자 이하)
     * @return 조정 뒤 학생의 명단 항목
     */
    @PatchMapping("/{studentId}/count/decrease")
    public VolunteerResponse decrease(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long studentId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return volunteerService.decrease(memberId, studentId, idempotencyKey);
    }

    /**
     * 학생을 오늘 당일 봉사자로 지정하고 봉사 알림을 보낸다. 봉사 횟수가 0이면 409 "봉사가 없습니다"다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 지정 뒤 학생의 명단 항목
     */
    @PostMapping("/{studentId}/duty")
    public VolunteerResponse assignDuty(@AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return volunteerService.assignDuty(memberId, studentId);
    }

    /**
     * 오늘 당일 봉사자 지정을 취소하고 그 알림을 지운다. 이미 완료했으면 409다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 취소 뒤 학생의 명단 항목
     */
    @DeleteMapping("/{studentId}/duty")
    public VolunteerResponse cancelDuty(@AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return volunteerService.cancelDuty(memberId, studentId);
    }
}
