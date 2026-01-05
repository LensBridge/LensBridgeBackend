package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "daily_content")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyContent {
    @Id
    private LocalDate date;
    private IslamicQuote verse;
    private IslamicQuote hadith;
}
