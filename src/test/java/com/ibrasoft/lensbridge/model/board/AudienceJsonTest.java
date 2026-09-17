package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Audience must serialise as the lowercase form the OpenAPI spec enumerates.
 *
 * springdoc derives the documented enum values from toString(), so the spec has
 * always said "brothers". Jackson, with no configuration, used name() and both
 * emitted and demanded "BROTHERS". Every generated client therefore sent a value
 * the server rejected, and every response disagreed with its own schema.
 */
class AudienceJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialisesAsLowercase() throws Exception {
        assertEquals("\"brothers\"", mapper.writeValueAsString(Audience.BROTHERS));
        assertEquals("\"sisters\"", mapper.writeValueAsString(Audience.SISTERS));
        assertEquals("\"both\"", mapper.writeValueAsString(Audience.BOTH));
    }

    @Test
    void readsTheDocumentedLowercaseForm() throws Exception {
        assertEquals(Audience.BROTHERS, mapper.readValue("\"brothers\"", Audience.class));
        assertEquals(Audience.SISTERS, mapper.readValue("\"sisters\"", Audience.class));
        assertEquals(Audience.BOTH, mapper.readValue("\"both\"", Audience.class));
    }

    /** Callers written against the previous uppercase behaviour must keep working. */
    @Test
    void stillReadsTheLegacyUppercaseForm() throws Exception {
        assertEquals(Audience.BROTHERS, mapper.readValue("\"BROTHERS\"", Audience.class));
        assertEquals(Audience.BOTH, mapper.readValue("\"Both\"", Audience.class));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(Exception.class, () -> mapper.readValue("\"everyone\"", Audience.class));
    }

    @Test
    void roundTrips() throws Exception {
        for (Audience a : Audience.values()) {
            assertEquals(a, mapper.readValue(mapper.writeValueAsString(a), Audience.class));
        }
    }
}
