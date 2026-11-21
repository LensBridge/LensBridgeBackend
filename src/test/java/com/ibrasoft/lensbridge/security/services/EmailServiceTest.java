package com.ibrasoft.lensbridge.security.services;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String URL = "https://app.example/reset-password?token=abc&next=1";

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService();
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(service, "fromName", "Mailer");
    }

    private String sentHtml() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        // Content-Type headers are only written once the message is finalised.
        captor.getValue().saveChanges();
        return htmlOf(captor.getValue());
    }

    private static String htmlOf(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                String html = htmlOf(body);
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }

    @Test
    void namesContainingRegexReplacementCharactersAreRenderedLiterally() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        service.sendPasswordResetEmail("to@example.com", "Cash $1 \\back $2", URL);

        assertThat(sentHtml()).contains("Cash $1 \\back $2");
    }

    @Test
    void nameIsHtmlEscapedSoMarkupCannotBeInjected() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        service.sendVerificationEmail("to@example.com", "<script>alert(1)</script>", URL);

        String html = sentHtml();
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void urlIsEscapedWhereItIsPlacedInTheTemplate() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        service.sendPasswordResetEmail("to@example.com", "Aisha", URL);

        String html = sentHtml();
        assertThat(html).contains("token=abc&amp;next=1");
        assertThat(html).doesNotContain("{{ACTIVATE_URL}}").doesNotContain("{{USER_NAME}}");
    }

    @Test
    void plainTextFallbackKeepsTheLink() {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));

        service.sendPasswordResetEmail("to@example.com", "Aisha", URL);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains(URL).contains("Aisha");
    }

    @Test
    void failureOfBothHtmlAndPlainTextSendsIsPropagated() {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendVerificationEmail("to@example.com", "Aisha", URL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send email");
    }
}
