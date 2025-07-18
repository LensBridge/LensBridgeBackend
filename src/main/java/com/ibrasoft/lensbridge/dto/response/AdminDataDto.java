package com.ibrasoft.lensbridge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminDataDto {
    private int totalUsers;
    private int totalUploaded;
    private int totalApproved;
    private int totalFeatured;
}
