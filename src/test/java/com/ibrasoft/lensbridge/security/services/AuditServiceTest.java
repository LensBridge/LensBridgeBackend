package com.ibrasoft.lensbridge.security.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    private final AuditService service = new AuditService();

    private ch.qos.logback.classic.Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        auditLogger = ctx.getLogger("AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        auditLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    private String lastMessage() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        return events.get(events.size() - 1).getFormattedMessage();
    }

    @Test
    void logSecurityEventIncludesEventUserAndDetails() {
        service.logSecurityEvent("CUSTOM_EVENT", "alice", "did something");

        String msg = lastMessage();
        assertThat(msg).contains("CUSTOM_EVENT");
        assertThat(msg).contains("User: alice");
        assertThat(msg).contains("Details: did something");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logLoginAttemptSuccessUsesLoginSuccessEvent() {
        service.logLoginAttempt("bob", true, "192.168.1.1");

        String msg = lastMessage();
        assertThat(msg).contains("LOGIN_SUCCESS");
        assertThat(msg).contains("User: bob");
        assertThat(msg).contains("IP: 192.168.1.1");
    }

    @Test
    void logLoginAttemptFailureUsesLoginFailedEvent() {
        service.logLoginAttempt("bob", false, "192.168.1.1");

        String msg = lastMessage();
        assertThat(msg).contains("LOGIN_FAILED");
        assertThat(msg).doesNotContain("LOGIN_SUCCESS");
    }

    @Test
    void logPasswordChangeEmitsPasswordChangedEvent() {
        service.logPasswordChange("carol", "10.0.0.5");

        String msg = lastMessage();
        assertThat(msg).contains("PASSWORD_CHANGED");
        assertThat(msg).contains("User: carol");
        assertThat(msg).contains("IP: 10.0.0.5");
    }

    @Test
    void logEmailVerificationEmitsEmailVerifiedEvent() {
        service.logEmailVerification("dave", "dave@example.com");

        String msg = lastMessage();
        assertThat(msg).contains("EMAIL_VERIFIED");
        assertThat(msg).contains("User: dave");
        assertThat(msg).contains("Email: dave@example.com");
    }

    @Test
    void logPasswordResetEmitsPasswordResetRequestedEvent() {
        service.logPasswordReset("erin", "erin@example.com");

        String msg = lastMessage();
        assertThat(msg).contains("PASSWORD_RESET_REQUESTED");
        assertThat(msg).contains("User: erin");
        assertThat(msg).contains("Email: erin@example.com");
    }

    @Test
    void messageContainsTimestampPrefix() {
        service.logSecurityEvent("E", "u", "d");

        // Format: [yyyy-MM-dd HH:mm:ss] E - User: u - Details: d
        assertThat(lastMessage()).matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] E - User: u - Details: d");
    }
}
