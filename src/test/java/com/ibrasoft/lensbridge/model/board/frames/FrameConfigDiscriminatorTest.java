package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameConfig;
import com.ibrasoft.lensbridge.model.minbar.board.frames.PromotableSocialMediaFrameConfig;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FrameConfig} declares its subtypes twice: once for Jackson, which decides what the
 * server actually sends, and once for springdoc, which decides what {@code openapi.yaml} tells
 * clients the server sends. Nothing in the compiler makes the two agree.
 * <p>
 * They disagreed once already. The {@code @Schema} mapping was absent entirely, so the spec
 * emitted a discriminator with no mapping, OpenAPI's implicit fallback is the schema name, and
 * every generated TypeScript frame config claimed {@code type: "PosterFrameConfig"} for a field
 * that carries {@code "poster"}. Nothing failed loudly — the board dispatches on
 * {@code frameType} — it just meant no client could narrow on the discriminator.
 */
class FrameConfigDiscriminatorTest {

    private static Map<String, Class<?>> jacksonSubtypes() {
        return Stream.of(FrameConfig.class.getAnnotation(JsonSubTypes.class).value())
                .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));
    }

    private static Map<String, Class<?>> springdocSubtypes() {
        return Stream.of(FrameConfig.class.getAnnotation(Schema.class).discriminatorMapping())
                .collect(Collectors.toMap(DiscriminatorMapping::value, DiscriminatorMapping::schema));
    }

    @Test
    void theSpecMappingMatchesWhatJacksonActuallySends() {
        assertThat(springdocSubtypes()).isEqualTo(jacksonSubtypes());
    }

    @Test
    void bothAgreeOnTheDiscriminatorPropertyName() {
        assertThat(FrameConfig.class.getAnnotation(Schema.class).discriminatorProperty())
                .isEqualTo(FrameConfig.class
                        .getAnnotation(com.fasterxml.jackson.annotation.JsonTypeInfo.class)
                        .property());
    }

    /**
     * The wire value is the thing both mappings claim it is. Pinned by serializing a real
     * instance rather than by re-reading the annotation, so this fails if Jackson's behaviour
     * ever diverges from its own declaration.
     */
    @Test
    void aSerialisedSocialsConfigCarriesTheMappedDiscriminator() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                PromotableSocialMediaFrameConfig.builder().url("https://example.com").build());

        assertThat(json).contains("\"type\":\"socials\"");
    }

    /** Every subtype the spec advertises must really extend the base it is mapped under. */
    @Test
    void everyMappedSchemaIsAFrameConfig() {
        assertThat(springdocSubtypes().values()).allSatisfy(
                type -> assertThat(FrameConfig.class).isAssignableFrom(type));
    }
}
