package com.ibrasoft.lensbridge.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class PatchTest {

    @Test
    void applyInvokesSetterWhenValueIsNonNull() {
        AtomicReference<String> sink = new AtomicReference<>("original");

        Patch.apply("new", sink::set);

        assertThat(sink.get()).isEqualTo("new");
    }

    @Test
    void applyDoesNotInvokeSetterWhenValueIsNull() {
        AtomicReference<String> sink = new AtomicReference<>("original");

        Patch.apply(null, sink::set);

        assertThat(sink.get()).isEqualTo("original");
    }

    @Test
    void applyForwardsExactValueToSetter() {
        Object value = new Object();
        AtomicReference<Object> captured = new AtomicReference<>();
        Consumer<Object> setter = captured::set;

        Patch.apply(value, setter);

        assertThat(captured.get()).isSameAs(value);
    }
}
