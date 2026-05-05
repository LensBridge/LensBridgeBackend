package com.ibrasoft.lensbridge.util;

import java.util.function.Consumer;

public final class Patch {
    private Patch() {}

    public static <T> void apply(T value, Consumer<T> setter) {
        if (value != null) setter.accept(value);
    }
}
