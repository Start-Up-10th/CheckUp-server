package com.checkup.checkup.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.user.dto.Response.UserSearchResponse;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerHistoryResponse;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.domain.user.serivce.UserSearchService;
import com.checkup.checkup.domain.user.serivce.UserVolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "학생 정보", description = "DataGSM 학생 정보와 봉사 횟수 조회")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSearchService userSearchService;
    private final UserVolunteerService userVolunteerService;

    /**
     * DataGSM 학생 id로 학생 정보를 조회한다. 관리자 또는 본인만 조회할 수 있다.
     */
    @Operation(summary = "학생 정보 조회", description = "DataGSM 학생 id로 조회한다. 관리자 또는 본인만 조회할 수 있다.")
    @GetMapping("/{studentId}")
    public UserSearchResponse findUser(@AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return userSearchService.findUser(memberId, studentId);
    }

    /**
     * DataGSM 학생 id로 누적 봉사 횟수를 조회한다. 관리자 또는 본인만 조회할 수 있다.
     */
    @Operation(summary = "학생 봉사 횟수 조회", description = "앞으로 해야 할 봉사 횟수다. 관리자 또는 본인만 조회할 수 있다.")
    @GetMapping("/{studentId}/volunteer")
    public UserVolunteerResponse findVolunteer(@AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return userVolunteerService.findVolunteer(memberId, studentId);
    }

    /**
     * DataGSM 학생 id로 봉사 완료 내역을 최신 운영일부터 조회한다. 관리자 또는 본인만 조회할 수 있다.
     */
    @Operation(summary = "학생 봉사 완료 내역 조회", description = "자치위원이 완료를 확인한 당일 봉사를 최신 운영일부터 돌려준다. 한 건이 봉사 1회다. 관리자 또는 본인만 조회할 수 있다.")
    @GetMapping("/{studentId}/volunteer/history")
    public UserVolunteerHistoryResponse findVolunteerHistory(
            @AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return userVolunteerService.findVolunteerHistory(memberId, studentId);
    }
}
