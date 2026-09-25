package com.ibrasoft.lensbridge.service.agent.http;

import com.ibrasoft.lensbridge.model.board.Device;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies {@link AuthenticatedDevice} parameters with the device {@link DeviceAuthInterceptor}
 * already authenticated. It never authenticates anything itself.
 */
@Component
public class AuthenticatedDeviceArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedDevice.class)
                && Device.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object device = webRequest.getAttribute(DeviceAuthInterceptor.DEVICE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (device == null) {
            throw new IllegalStateException("DeviceAuthInterceptor did not run for " + parameter.getExecutable());
        }
        return device;
    }
}
