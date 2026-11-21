package com.ibrasoft.lensbridge.dto.board.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned exactly once when an admin issues a new enrollment token.
 * The plaintext token is shown to the operator and never persisted.
 */
@Data
@Builder
public class IssueEnrollmentTokenResponse {

    private UUID tokenId;

    /** Plaintext token: 24 characters of URL-safe base64 without padding. Show once, never store. */
    private String token;

    private Instant expiresAt;
}
