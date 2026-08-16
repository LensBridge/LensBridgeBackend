package com.ibrasoft.lensbridge.model.board.frames;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FrameType must serialise as the lowercase form the OpenAPI spec enumerates.
 *
 * The same defect {@code AudienceJsonTest} pins, in the enum that was missed:
 * springdoc documented {@code toString()} while Jackson used {@code name()}, so
 * {@code FrameDefinition.frameType} was published as {@code poster} and sent as
 * {@code POSTER}. The admin console rendered every frame as an unlabelled grey
 * box, and the kiosk's poster prefetch — which compares against {@code "poster"}
 * — silently matched nothing.
 *
 * {@code FrameTypeTest} already asserted {@code toString()}, and passed the whole
 * time. That is the trap: {@code toString()} is not the serialised form unless
 * something says it is.
 */
class FrameTypeJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialisesAsLowercase() throws Exception {
        assertEquals("\"poster\"", mapper.writeValueAsString(FrameType.POSTER));
        assertEquals("\"agenda\"", mapper.writeValueAsString(FrameType.AGENDA));
        assertEquals("\"next_prayer\"", mapper.writeValueAsString(FrameType.NEXT_PRAYER));
        assertEquals("\"jummah\"", mapper.writeValueAsString(FrameType.JUMMAH));
        assertEquals("\"islamic_quote\"", mapper.writeValueAsString(FrameType.ISLAMIC_QUOTE));
    }

    @Test
    void readsTheDocumentedLowercaseForm() throws Exception {
        assertEquals(FrameType.POSTER, mapper.readValue("\"poster\"", FrameType.class));
        assertEquals(FrameType.ISLAMIC_QUOTE, mapper.readValue("\"islamic_quote\"", FrameType.class));
    }

    /** Consumers written against the previous uppercase behaviour must keep working. */
    @Test
    void stillReadsTheLegacyUppercaseForm() throws Exception {
        assertEquals(FrameType.POSTER, mapper.readValue("\"POSTER\"", FrameType.class));
        assertEquals(FrameType.ISLAMIC_QUOTE, mapper.readValue("\"Islamic_Quote\"", FrameType.class));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(Exception.class, () -> mapper.readValue("\"carousel\"", FrameType.class));
    }

    @Test
    void roundTrips() throws Exception {
        for (FrameType type : FrameType.values()) {
            assertEquals(type, mapper.readValue(mapper.writeValueAsString(type), FrameType.class));
        }
    }

    /**
     * The value the spec publishes is the value a client receives. Pinned against
     * the enum rather than a literal list so a new frame type cannot skip it.
     */
    @Test
    void serialisedFormMatchesToString() throws Exception {
        for (FrameType type : FrameType.values()) {
            assertEquals("\"" + type + "\"", mapper.writeValueAsString(type));
        }
    }
}
