package com.checkup.checkup.global.config;

import com.checkup.checkup.global.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
            ModelConverters.getInstance().read(ErrorResponse.class)
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
}
