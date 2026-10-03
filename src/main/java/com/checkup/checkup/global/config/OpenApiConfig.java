package com.checkup.checkup.global.config;

import com.checkup.checkup.global.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * 로그인은 SESSION 쿠키로 하므로 문서에 쿠키 인증 방식을 표시한다.
 * 모든 API의 오류 응답은 공통 ErrorResponse 형식이라, 각 API에 default 오류 응답으로 붙인다.
 */
@Configuration
public class OpenApiConfig {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String ERROR_RESPONSE = "ErrorResponse";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Bean
    public OpenAPI checkupOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CheckUp API")
                        .description("기숙사 출석 관리 서버 API. 로그인은 DataGSM OAuth 후 발급되는 SESSION 쿠키로 유지한다.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SESSION_COOKIE)
                                .description("DataGSM 로그인 후 서버가 발급하는 세션 쿠키")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE));
    }

    /**
     * 공통 오류 응답 스키마를 등록하고, 오류 응답이 적혀 있지 않은 API에 default 응답으로 붙인다.
     */
    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            // readAll: ErrorResponse가 참조하는 FieldError 스키마까지 함께 등록한다.
            ModelConverters.getInstance().readAll(ErrorResponse.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            ApiResponse errorResponse = new ApiResponse()
                    .description("오류. code는 ErrorCode 이름, errors는 요청 검증 실패 때만 포함된다.")
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE))));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() != null && !operation.getResponses().containsKey("default")) {
                    operation.getResponses().addApiResponse("default", errorResponse);
                }
            }));
        };
    }

    /**
     * 로그아웃은 컨트롤러가 아니라 Spring Security 로그아웃 필터가 처리해 자동 문서에 나오지 않는다. 문서에만 직접 추가한다.
     */
    @Bean
    public OpenApiCustomizer logoutPathCustomizer() {
        return openApi -> openApi.path(LOGOUT_PATH, new PathItem().post(new Operation()
                .addTagsItem("인증")
                .summary("로그아웃")
                .description("세션을 무효화하고 SESSION 쿠키를 지운다. 로그인하지 않았어도 204다. 열려 있던 QR·얼굴 인식 세션도 정리된다.")
                .operationId("logout")
                .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("로그아웃 완료")))));
    }
}
