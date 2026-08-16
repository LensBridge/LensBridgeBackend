package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.SocialType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SocialType must serialise as the lowercase label, like {@code Audience} and
 * {@code FrameType} before it. Without {@code @JsonValue} Jackson publishes
 * {@code INSTAGRAM} while the frontend keys its platform icons off {@code instagram},
 * and every socials frame renders unbranded.
 */
class SocialTypeJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialisesAsLowercase() throws Exception {
        assertEquals("\"instagram\"", mapper.writeValueAsString(SocialType.INSTAGRAM));
        assertEquals("\"whatsapp\"", mapper.writeValueAsString(SocialType.WHATSAPP));
        assertEquals("\"other\"", mapper.writeValueAsString(SocialType.OTHER));
    }

    @Test
    void readsTheLowercaseForm() throws Exception {
        assertEquals(SocialType.INSTAGRAM, mapper.readValue("\"instagram\"", SocialType.class));
        assertEquals(SocialType.TIKTOK, mapper.readValue("\"tiktok\"", SocialType.class));
    }

    /** Anything already persisted or sent as the enum name must keep binding. */
    @Test
    void stillReadsTheUppercaseEnumName() throws Exception {
        assertEquals(SocialType.INSTAGRAM, mapper.readValue("\"INSTAGRAM\"", SocialType.class));
        assertEquals(SocialType.YOUTUBE, mapper.readValue("\"YouTube\"", SocialType.class));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(Exception.class, () -> mapper.readValue("\"myspace\"", SocialType.class));
    }

    @Test
    void roundTrips() throws Exception {
        for (SocialType type : SocialType.values()) {
            assertEquals(type, mapper.readValue(mapper.writeValueAsString(type), SocialType.class));
        }
    }

    /** The value the spec publishes is the value a client receives. */
    @Test
    void serialisedFormMatchesToString() throws Exception {
        for (SocialType type : SocialType.values()) {
            assertEquals("\"" + type + "\"", mapper.writeValueAsString(type));
        }
    }
}