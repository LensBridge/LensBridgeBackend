package com.ibrasoft.lensbridge.service.agent.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body has been read once, up front, so it can be read again: once by
 * {@link DeviceAuthInterceptor} to check the signature over the exact bytes sent, and once by
 * Spring to bind {@code @RequestBody}. Created by {@link DeviceBodyCachingFilter}.
 */
public class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    /** The body exactly as received. */
    public byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return in.read(); }
            @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            @Override public boolean isFinished() { return in.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("cached body is read synchronously");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }
}
