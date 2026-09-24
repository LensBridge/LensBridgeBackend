package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.service.board.offline.OfflineBundleService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Body of {@code POST /api/agent/content-bundle}. Both fields are optional; an absent body
 * means all defaults. Unknown fields are ignored, so newer agents can send more.
 */
@Data
public class ContentBundleRequest {

    /**
     * An online board syncs every 30 minutes, so a week is plenty of runway for an outage and
     * keeps each sync small. Admin downloads for offline boards default to two weeks instead
     * ({@link OfflineBundleService#DEFAULT_DAYS}).
     */
    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_HAVE_MEDIA = 2000;
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";

    /** Days in the package, starting today in the device's timezone. */
    @Schema(minimum = "1", maximum = "" + OfflineBundleService.MAX_DAYS, defaultValue = "" + DEFAULT_DAYS)
    @Min(1)
    @Max(OfflineBundleService.MAX_DAYS)
    private Integer days;

    /**
     * SHA-256 (64 lowercase hex) of media the board's store already holds. Those files are left
     * out of the zip but stay listed, and signed, in {@code mbu.json}.
     */
    @ArraySchema(maxItems = MAX_HAVE_MEDIA, schema = @Schema(pattern = SHA256_HEX))
    @Size(max = MAX_HAVE_MEDIA)
    private List<@Pattern(regexp = SHA256_HEX, message = "must be 64 lowercase hex characters (SHA-256)") String> haveMedia;

    public int daysOrDefault() {
        return days == null ? DEFAULT_DAYS : days;
    }

    public Set<String> haveMediaSet() {
        return haveMedia == null ? Set.of() : new LinkedHashSet<>(haveMedia);
    }
}
