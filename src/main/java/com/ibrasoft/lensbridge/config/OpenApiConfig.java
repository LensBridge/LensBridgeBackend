package com.ibrasoft.lensbridge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDevice;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import io.swagger.v3.core.converter.ModelConverters;
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

import java.util.Arrays;
import java.util.List;

@Configuration
public class OpenApiConfig {

    static {
        // Workaround for @CurrentUser being an argument, which springdoc cannot infer
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
        // And @AuthenticatedDevice, which deviceAuthCustomizer documents as headers instead.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticatedDevice.class);

        // Likewise Pageable, which is documented as a single object-typed query
        // parameter instead of the page/size/sort trio the resolver actually reads.
        SpringDocUtils.getConfig().replaceWithClass(
                org.springframework.data.domain.Pageable.class,
                org.springdoc.core.converters.models.Pageable.class);
    }

    @Value("${lensbridge.app.version:unknown}")
    private String appVersion;

    /**
     * Documents the device signature once for every endpoint that takes an
     * {@link AuthenticatedDevice}: the three X-MB-* headers and the 401. The controllers stay
     * free of the same twenty lines of annotations each.
     */
    @Bean
    public OperationCustomizer deviceAuthCustomizer() {
        return (operation, handlerMethod) -> {
            boolean deviceAuthenticated = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(AuthenticatedDevice.class));
            if (!deviceAuthenticated) {
                return operation;
            }
            operation.addParametersItem(deviceHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER,
                    "Enrolled device id", new StringSchema().format("uuid")));
            operation.addParametersItem(deviceHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER,
                    "Unix milliseconds; must be within 5 minutes of the server clock",
                    new IntegerSchema().format("int64")));
            operation.addParametersItem(deviceHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER,
                    "Base64 Ed25519 signature by the device key over musallahboard-http-v1, the method, "
                            + "path, device id, timestamp and the body's SHA-256",
                    new StringSchema()));
            operation.getResponses().addApiResponse("401", new ApiResponse()
                    .description("Missing or bad device signature, clock skew over 5 minutes, "
                            + "or an unknown or revoked device")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/MessageResponse")))));
            return operation;
        };
    }

    private static HeaderParameter deviceHeader(String name, String description, Schema<?> schema) {
        return (HeaderParameter) new HeaderParameter().name(name).required(true).description(description).schema(schema);
    }

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

    /**
     * No endpoint returns {@link MusallahBoardPayload} any more, but it is still the format of
     * each {@code payloads/<date>.json} inside a content package, and the board generates its
     * types from this spec. Registered by hand so springdoc does not drop it as unreferenced.
     * Schemas already present are left alone.
     */
    @Bean
    public OpenApiCustomizer contentPackageSchemasCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance(true).readAll(MusallahBoardPayload.class)
                    .forEach((name, schema) -> {
                        if (openApi.getComponents().getSchemas() == null
                                || !openApi.getComponents().getSchemas().containsKey(name)) {
                            openApi.getComponents().addSchemas(name, schema);
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
