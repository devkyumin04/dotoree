package com.kyumin.dotoree.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// API 문서(Swagger UI)의 공통 부분 — 제목 · Authorize 버튼(Bearer) · 공통 에러 응답 (ADR-074)
// 메서드마다 설명을 달지 않는다: 컨트롤러가 어노테이션으로 덮이고, 손으로 쓴 설명은 코드와 어긋나는 부채가 된다
@Configuration
public class OpenApiConfig {

	private static final String BEARER = "bearerAuth";
	private static final String PROBLEM = "ProblemDetail";

	@Bean
	public OpenAPI dotoreeOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("도토리 API")
						.version("v1")
						.description("AI 영수증 인식 가계부 도토리의 API.\n\n"
								+ "**눌러 보는 법** — `POST /api/auth/demo-login` 을 Try it out 으로 실행하면 응답에 `accessToken` 이 옵니다. "
								+ "그 값을 오른쪽 위 **Authorize** 에 붙여 넣으면 나머지 API 를 부를 수 있습니다. "
								+ "데모 계정은 읽기 전용이라 조회는 되고 쓰기(등록 · 수정 · 삭제)는 403 입니다.\n\n"
								+ "에러 응답은 모두 ProblemDetail(RFC 9457) 모양입니다. 한 글자 코드(`C` · `E` 등)의 뜻은 각 칸의 설명에 있습니다."))
				.components(new Components()
						.addSecuritySchemes(BEARER, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Access 토큰(15분). 로그인 · 가입 · 데모 로그인 응답의 accessToken 을 그대로 넣는다 — 'Bearer ' 는 붙이지 않는다")));
	}

	// 모든 주소에 같은 규칙을 한 곳에서 붙인다 — 컨트롤러 메서드마다 반복하지 않으려고
	//  · 로그인이 필요한 주소엔 자물쇠(Bearer)와 401, 요청 값이 있는 주소엔 400, {번호} 가 있는 주소엔 404
	//  · "로그인 없이 열린 주소"의 기준은 SecurityConfig 의 permitAll 과 같아야 한다 — 거기를 바꾸면 여기도
	@Bean
	public OpenApiCustomizer commonResponses() {
		return openApi -> {
			// 스키마는 여기서 등록한다 — OpenAPI 빈에 넣어 두면 springdoc 이 "아무도 참조하지 않는 스키마"로 보고 지워 버려,
			// 아래에서 붙이는 $ref 가 끊긴다 (ApiDocsIntegrationTest 가 잡았다)
			openApi.getComponents().addSchemas(PROBLEM, problemDetail());
			openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
				if (!isPublic(path)) {
					operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
					addIfAbsent(operation, "401", "로그인이 필요합니다 (토큰이 없거나 만료)");
				}
				boolean hasInput = operation.getRequestBody() != null
						|| (operation.getParameters() != null && !operation.getParameters().isEmpty());
				if (hasInput) {
					addIfAbsent(operation, "400", "입력값이 올바르지 않습니다");
				}
				if (path.contains("{")) {
					addIfAbsent(operation, "404", "없는 번호이거나 내 것이 아닙니다");
				}
			}));
		};
	}

	private static boolean isPublic(String path) {
		return path.equals("/api/users/signup") || path.equals("/api/users/login") || path.startsWith("/api/auth/");
	}

	private static void addIfAbsent(Operation operation, String status, String description) {
		if (operation.getResponses().containsKey(status)) {
			return;
		}
		Schema<?> ref = new Schema<>().$ref(PROBLEM);
		operation.getResponses().addApiResponse(status, new ApiResponse()
				.description(description)
				.content(new Content().addMediaType("application/problem+json", new MediaType().schema(ref))));
	}

	// GlobalExceptionHandler 가 돌려주는 모양 그대로 (ADR-061)
	private static Schema<?> problemDetail() {
		return new ObjectSchema()
				.description("에러 응답 (RFC 9457 ProblemDetail). 사람에게 보여 줄 문구는 detail")
				.addProperty("title", new StringSchema().example("Bad Request"))
				.addProperty("status", new IntegerSchema().example(400))
				.addProperty("detail", new StringSchema().example("입력값이 올바르지 않습니다."))
				.addProperty("instance", new StringSchema().example("/api/accounts"));
	}
}
