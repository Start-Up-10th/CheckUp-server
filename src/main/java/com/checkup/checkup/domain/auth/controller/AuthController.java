package com.checkup.checkup.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.CurrentMemberService;
import com.checkup.checkup.domain.auth.service.LoginResult;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.global.config.WebProperties;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.DataGsmErrorCodes;
import com.checkup.checkup.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;

import java.net.URI;

/**
 * DataGSM OAuth 로그인과 세션 사용자 조회 API.
 *
 * 로그아웃({@code POST /api/v1/auth/logout})은 컨트롤러가 아니라
 * {@link com.checkup.checkup.global.security.SecurityConfig}의 Spring Security 로그아웃 필터가 처리한다.
 */
@Slf4j
@Tag(name = "인증", description = "DataGSM OAuth 로그인과 세션 사용자 조회. 로그아웃은 POST /api/v1/auth/logout")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginSessionService loginSessionService;
    private final CurrentMemberService currentMemberService;
    private final WebProperties webProperties;

    /**
     * DataGSM 로그인 페이지로 보낸다.
     *
     * @param redirect 로그인 후 돌아갈 웹 경로(예: {@code /admin/qr}). 같은 웹 안의 상대 경로만 쓰고,
     *                 없거나 안전하지 않으면 웹 로그인 완료 화면({@code /login/complete})으로 돌아간다.
     * @return state·PKCE가 포함된 DataGSM 인가 URL로의 302 응답
     */
    @SecurityRequirements
    @Operation(summary = "DataGSM 로그인 시작", description = "DataGSM 로그인 페이지로 302 리다이렉트한다. redirect는 로그인 뒤 돌아갈 웹 경로(선택)이며, 없거나 안전하지 않으면 /login/complete로 돌아간다.")
    @GetMapping("/login")
    public ResponseEntity<Void> login(@Parameter(description = "로그인 뒤 돌아갈 웹 경로(선택). 같은 웹 안의 상대 경로만 받고, 없거나 안전하지 않으면 /login/complete", example = "/admin") @RequestParam(required = false) String redirect) {
        String url = authService.createLoginUrl(redirect);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * DataGSM 인가 후 돌아오는 콜백. 토큰을 교환해 회원을 저장하고 로그인 세션을 만든 뒤 웹으로 돌려보낸다.
     *
     * 브라우저가 서버 주소에 머물지 않도록 실패도 웹 로그인 화면({@code /login?error=<ErrorCode>})으로 보낸다.
     * code·state가 없으면(DataGSM에서 로그인을 취소한 경우 등) {@code INVALID_REQUEST}다.
     *
     * @param code  DataGSM 인가 코드
     * @param state 로그인 시작 때 발급한 state
     * @return 성공이면 로그인 시작 때 정한 웹 경로(기본 {@code /login/complete}), 실패면 웹 로그인 화면으로의 302 응답
     */
    @SecurityRequirements
    @Operation(summary = "DataGSM 로그인 콜백", description = "DataGSM이 호출한다. 회원을 저장하고 SESSION 쿠키를 만든 뒤 웹으로 302 리다이렉트한다. 실패도 웹 /login?error=<ErrorCode>로 보낸다.")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @Parameter(description = "DataGSM 인가 코드. DataGSM이 붙여 보낸다") @RequestParam(required = false) String code,
            @Parameter(description = "로그인 시작 때 발급한 state. DataGSM이 붙여 보낸다") @RequestParam(required = false) String state,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return redirectToLoginFailure(ErrorCode.INVALID_REQUEST);
        }
        try {
            LoginResult result = authService.completeLogin(code, state);
            loginSessionService.login(result.member(), httpRequest, httpResponse);
            return redirectToWeb(result.redirectPath());
        } catch (CustomException e) {
            return redirectToLoginFailure(e.getErrorCode());
        } catch (DataGsmException e) {
            log.warn("DataGSM login failed: {}", e.getClass().getSimpleName());
            return redirectToLoginFailure(DataGsmErrorCodes.of(e));
        }
    }

    /**
     * 세션 쿠키로 현재 로그인한 회원을 조회한다. 로그인하지 않았으면 401을 반환한다.
     *
     * @param memberId 세션에 저장된 회원 id
     * @return 현재 회원의 이름, 역할, 필수 동의 여부, 학생 정보(학생이 아니면 null)
     */
    @Operation(summary = "현재 로그인 회원 조회", description = "SESSION 쿠키의 회원 정보, 필수 동의 여부(consented), 본인 학생 정보(student: DataGSM 학생 id·학년·반·번호·학번·호실·층)를 돌려준다. 학생 정보가 없는 회원(교사)은 student가 null이다. 로그인하지 않았으면 401 UNAUTHORIZED.")
    @GetMapping("/me")
    public OAuthLoginResponse me(@AuthenticationPrincipal Long memberId) {
        return currentMemberService.getCurrentMember(memberId);
    }

    private ResponseEntity<Void> redirectToLoginFailure(ErrorCode errorCode) {
        return redirectToWeb("/login?error=" + errorCode.getCode());
    }

    private ResponseEntity<Void> redirectToWeb(String path) {
        String baseUrl = webProperties.baseUrl();
        String origin = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(origin + path)).build();
    }
}
