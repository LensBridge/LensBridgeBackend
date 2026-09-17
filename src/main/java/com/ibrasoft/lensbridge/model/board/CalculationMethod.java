package com.ibrasoft.lensbridge.model.board;

public enum CalculationMethod {
    KARACHI(1), ISNA(2), MWL(3), MAKKAH(4), EGYPT(5),
    TEHRAN(7), GULF(8), KUWAIT(9), QATAR(10), SINGAPORE(11),
    FRANCE(12), TURKEY(13), RUSSIA(14), DUBAI(16);

    private final int code;
    CalculationMethod(int code) { this.code = code; }
    public int getCode() { return code; }
}
