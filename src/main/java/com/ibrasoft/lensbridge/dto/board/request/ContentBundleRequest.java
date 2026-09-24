package com.ibrasoft.lensbridge.dto.board.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Body of {@code POST /api/agent/content-bundle}. Both fields are optional.
 * <p>
 * Parsed by hand from the raw request bytes rather than bound by Spring, because the device
 * signature covers the exact bytes that were sent; see
 * {@link com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator}.
 */
@Data
public class ContentBundleRequest {

    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_HAVE_MEDIA = 2000;

    /** Days in the package, starting today in the device's timezone. */
    @Schema(minimum = "1", maximum = "31", defaultValue = "" + DEFAULT_DAYS)
    private Integer days;

    /**
     * SHA-256 (64 lowercase hex) of media the board's store already holds. Those files are left
     * out of the zip but stay listed, and signed, in {@code mbu.json}.
     */
    @ArraySchema(maxItems = MAX_HAVE_MEDIA, schema = @Schema(pattern = "^[0-9a-f]{64}$"))
    private List<String> haveMedia;
}
