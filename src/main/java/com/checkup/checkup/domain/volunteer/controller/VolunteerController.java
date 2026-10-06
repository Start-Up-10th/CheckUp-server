package com.checkup.checkup.domain.volunteer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.volunteer.dto.request.VolunteerAdjustRequest;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerAdjustmentResponse;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 봉사 관리 API(DEC-020). 사감·기숙사 자치위원이 전체 학생 명단을 보고 봉사 횟수를 조정한다.
 */
@Tag(name = "봉사 관리", description = "사감·기숙사 자치위원의 봉사 관리 명단·횟수 조정·당일 봉사자(REQ-COM-001·002·006, DEC-020·021). 경로의 studentId는 DataGSM 학생 id")
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
     * @param floor    층(412호면 4). 없으면 전체 층
     * @param q        검색어. 숫자면 호실 번호·학번이 정확히 같은 학생, 그 밖에는 이름(포함·초성·오타 1개)
     * @param minCount 최소 봉사 횟수. 당일 봉사자 지정 후보는 {@code minCount=1}로 찾는다. 없으면 제한 없다.
     * @param onDuty   {@code true}면 오늘 당일 봉사자로 지정된 학생만 본다(자치위원의 완료 확인용).
     */
    @Operation(summary = "봉사 관리 명단", description = "전체 학생을 호실순으로 돌려준다. q는 숫자면 호실·학번 정확 일치, 그 밖에는 이름(포함 → 초성 → 오타 1개). floor는 층, minCount=1은 당일 봉사자 후보, onDuty=true는 오늘 봉사자만.")
    @GetMapping
    public List<VolunteerResponse> getVolunteers(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "층으로 거르기(선택). 값이 있으면 호실 미배정 학생은 빠진다", example = "3") @RequestParam(required = false) Integer floor,
            @Parameter(description = "검색어(선택). 숫자면 호실·학번 정확 일치, 그 밖에는 이름(포함 → 초성 → 오타 1개)", example = "홍길동") @RequestParam(required = false) String q,
            @Parameter(description = "남은 봉사 횟수가 이 값 이상인 학생만(선택). 1이면 당일 봉사자 후보", example = "1") @RequestParam(required = false) Integer minCount,
            @Parameter(description = "true면 오늘 당일 봉사자로 지정된 학생만(완료 포함, 선택)", example = "true") @RequestParam(required = false) Boolean onDuty
    ) {
        return volunteerService.getVolunteers(memberId, floor, q, minCount, onDuty);
    }

    /**
     * 학생 한 명의 봉사 횟수 조정 이력을 최신순으로 돌려준다(#140).
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @param limit     최대 개수(선택, 1~100). 없으면 50
     * @return 조정 이력
     */
    @Operation(summary = "봉사 횟수 조정 이력", description = "관리자 전용. 조정 시각·실제 바뀐 횟수·요청 횟수·사유·종류를 최신순으로 돌려준다. 당일 봉사 완료의 차감(-1)도 kind=DUTY_COMPLETION, reason=null로 포함한다. limit은 1~100(기본 50), 벗어나면 400 INVALID_REQUEST.")
    @GetMapping("/{studentId}/adjustments")
    public List<VolunteerAdjustmentResponse> getAdjustments(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId,
            @Parameter(description = "최대 개수(선택, 1~100). 없으면 50", example = "50") @RequestParam(required = false) Integer limit
    ) {
        return volunteerService.getAdjustments(memberId, studentId, limit);
    }

    /**
     * 봉사 횟수를 1 늘린다. 웹은 클릭마다 새 {@code Idempotency-Key}를 보내면 재시도가 중복 반영되지 않는다.
     *
     * @param memberId       세션의 회원 id
     * @param studentId      DataGSM 학생 id
     * @param idempotencyKey 재시도 방지 키(선택, 100자 이하)
     * @return 조정 뒤 학생의 명단 항목
     */
    @Operation(summary = "봉사 +1", description = "Idempotency-Key(선택)를 보내면 같은 키의 재시도는 한 번만 반영한다. 다른 학생·방향에 쓴 키는 409 IDEMPOTENCY_KEY_REUSED.")
    @PatchMapping("/{studentId}/count/increase")
    public VolunteerResponse increase(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId,
            @Parameter(description = "재시도 중복 방지 키(선택, 최대 100자). 같은 키로 다시 보내면 한 번만 반영한다", example = "9f0c1e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
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
    @Operation(summary = "봉사 −1(감면)", description = "사감의 감면에 쓴다. 횟수가 0이면 409 VOLUNTEER_COUNT_ZERO. Idempotency-Key는 증가와 같다.")
    @PatchMapping("/{studentId}/count/decrease")
    public VolunteerResponse decrease(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId,
            @Parameter(description = "재시도 중복 방지 키(선택, 최대 100자). 같은 키로 다시 보내면 한 번만 반영한다", example = "9f0c1e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return volunteerService.decrease(memberId, studentId, idempotencyKey);
    }

    /**
     * 봉사 횟수를 여러 회 늘리거나 줄이고 사유를 남긴다(#139). 차감은 남은 횟수까지만 한다.
     *
     * @param memberId       세션의 회원 id
     * @param studentId      DataGSM 학생 id
     * @param idempotencyKey 재시도 방지 키(선택, 100자 이하)
     * @param request        바꿀 횟수와 사유
     * @return 조정 뒤 학생의 명단 항목
     */
    @Operation(summary = "봉사 횟수 조정(여러 회·사유)", description = "delta는 -99~99(0 제외), reason은 선택(최대 100자). 차감은 남은 횟수까지만 하고 남은 횟수가 0이면 409 VOLUNTEER_COUNT_ZERO. Idempotency-Key는 증가와 같으며, 같은 키를 다른 학생·다른 delta에 쓰면 409 IDEMPOTENCY_KEY_REUSED.")
    @PatchMapping("/{studentId}/count")
    public VolunteerResponse adjust(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId,
            @Parameter(description = "재시도 중복 방지 키(선택, 최대 100자). 같은 키로 다시 보내면 한 번만 반영한다", example = "9f0c1e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody VolunteerAdjustRequest request
    ) {
        return volunteerService.adjustCount(memberId, studentId, idempotencyKey, request.delta(), request.reason());
    }

    /**
     * 학생을 오늘 당일 봉사자로 지정하고 봉사 알림을 보낸다. 봉사 횟수가 0이면 409 "봉사가 없습니다"다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 지정 뒤 학생의 명단 항목
     */
    @Operation(summary = "당일 봉사자 지정", description = "학생에게 봉사 알림을 만든다. 봉사 0회면 409 NO_VOLUNTEER_LEFT(봉사가 없습니다.), 이미 지정됐으면 409 ALREADY_ON_DUTY.")
    @PostMapping("/{studentId}/duty")
    public VolunteerResponse assignDuty(@AuthenticationPrincipal Long memberId, @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId) {
        return volunteerService.assignDuty(memberId, studentId);
    }

    /**
     * 오늘 당일 봉사자 지정을 취소하고 그 알림을 지운다. 이미 완료했으면 409다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 취소 뒤 학생의 명단 항목
     */
    @Operation(summary = "당일 봉사자 지정 취소", description = "그 지정의 알림을 지운다. 지정이 없으면 404 NOT_ON_DUTY, 완료했으면 409 DUTY_ALREADY_COMPLETED.")
    @DeleteMapping("/{studentId}/duty")
    public VolunteerResponse cancelDuty(@AuthenticationPrincipal Long memberId, @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId) {
        return volunteerService.cancelDuty(memberId, studentId);
    }

    /**
     * 오늘 당일 봉사를 완료로 표시하고 봉사 횟수를 1 줄인다. 자치위원이 봉사를 확인했을 때 누른다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 완료 뒤 학생의 명단 항목
     */
    @Operation(summary = "당일 봉사 완료", description = "자치위원이 봉사를 확인했을 때 누른다. 완료 표시와 봉사 −1을 함께 하며 한 번만 반영된다. 두 번째는 409 DUTY_ALREADY_COMPLETED.")
    @PostMapping("/{studentId}/duty/complete")
    public VolunteerResponse completeDuty(@AuthenticationPrincipal Long memberId, @Parameter(description = "DataGSM 학생 id(명단의 studentId). DB id가 아니다", example = "1") @PathVariable Long studentId) {
        return volunteerService.completeDuty(memberId, studentId);
    }
}
