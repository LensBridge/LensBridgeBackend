package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "weekly_content")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyContent {
    @Id
    private WeekId weekId;
    private IslamicQuote verse;
    private IslamicQuote hadith;
    private JummahPrayer[] jummahPrayer;
}
