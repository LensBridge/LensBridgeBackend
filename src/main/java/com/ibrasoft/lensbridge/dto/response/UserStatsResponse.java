package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserStatsResponse {
    private int totalUploads;
    private int approvedUploads;
    private int featuredUploads;
    private int pendingUploads;
}
