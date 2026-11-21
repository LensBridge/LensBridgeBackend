package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * The editorial slice of {@code DeviceConfig}: the scrolling message ticker.
 * <p>
 * Split out from {@link UpdateBoardConfigRequest} so ticker copy can be edited with
 * {@code board:ticker:write} alone. The rest of the config sets the board's coordinates,
 * which silently change every prayer time on it — not something a copy edit should reach.
 * Both fields are nullable and ignored when null, matching the PATCH semantics of the
 * config endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTickerRequest {

    private Boolean enableScrollingMessage;

    private List<@Size(max = DeviceConfig.MESSAGE_MAX,
            message = "Each scrolling message must be at most 4000 characters") String> scrollingMessages;
}
