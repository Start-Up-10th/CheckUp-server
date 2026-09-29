package com.checkup.checkup.domain.user.controller;

import com.checkup.checkup.domain.user.dto.Response.UserSearchResponse;
import com.checkup.checkup.domain.user.serivce.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * DataGSM 학생 id로 학생 정보를 조회한다. 관리자 또는 본인만 조회할 수 있다.
     */
    @GetMapping("/{studentId}")
    public UserSearchResponse findUser(@AuthenticationPrincipal Long memberId, @PathVariable Long studentId) {
        return userService.findUser(memberId, studentId);
    }
}
