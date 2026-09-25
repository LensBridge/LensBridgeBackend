package com.ibrasoft.lensbridge.service.agent.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller parameter of type {@link com.ibrasoft.lensbridge.model.board.Device} as
 * "the board that signed this request". An endpoint that declares one is device-authenticated:
 * {@link DeviceAuthInterceptor} verifies the X-MB-* signature before anything else about the
 * request is looked at, and answers 401 if it does not verify. The endpoint itself never sees
 * an unauthenticated request.
 *
 * <pre>
 * &#64;GetMapping("/weather")
 * public AgentWeatherResponse weather(&#64;AuthenticatedDevice Device device) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedDevice {
}
