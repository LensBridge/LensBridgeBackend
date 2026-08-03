package com.ibrasoft.lensbridge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import com.ibrasoft.lensbridge.security.CurrentUser;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    static {
        // Workaround for @CurrentUser being an argument, which springdoc cannot infer
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);

        // Likewise Pageable, which is documented as a single object-typed query
        // parameter instead of the page/size/sort trio the resolver actually reads.
        SpringDocUtils.getConfig().replaceWithClass(
                org.springframework.data.domain.Pageable.class,
                org.springdoc.core.converters.models.Pageable.class);
    }

    @Value("${lensbridge.app.version:unknown}")
    private String appVersion;

    @Bean
    public OpenApiCustomizer defaultErrorResponseCustomizer() {
        return openApi -> {
            Schema<?> messageResponse = new Schema<>()
                    .$ref("#/components/schemas/MessageResponse");
            ApiResponse fallback = new ApiResponse()
                    .description("Request failed; body carries a human-readable message")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(messageResponse)));

            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses != null && responses.getDefault() == null) {
                            responses.addApiResponse("default", fallback);
                        }
                    });
        };
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LensBridge API")
                        .version(appVersion)
                        .description("LensBridge backend — media upload and MusallahBoard management"))
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
