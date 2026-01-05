package com.ibrasoft.lensbridge.model.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IslamicQuote {
    private String arabic;
    private String transliteration;
    private String translation;
    private String reference;
}
