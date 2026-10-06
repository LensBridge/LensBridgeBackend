package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@lensbridge.tech}")
    private String fromAddress;

    @Value("${spring.mail.from.name:LensBridge Mailer Service}")
    private String fromName;

    /**
     * Sends a simple email.
     * 
     * @param to      the recipient's email address
     * @param subject the subject of the email
     * @param text    the body of the email
     */
    public void sendEmail(String to, String subject, String text) {
        try {
            logger.info("Attempting to send email to: {} with subject: {}", to, subject);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send email to: {} - Error: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Sends a verification email with a link.
     *
     * @param to              the recipient's email address
     * @param name            the recipient's first name (user-controlled, so HTML-escaped)
     * @param verificationUrl the link that confirms the address
     */
    public void sendVerificationEmail(String to, String name, String verificationUrl) {
        String subject = "Verify Your Email Address";
        String htmlContent = fillTemplate("email-verification.html", name, verificationUrl);
        String plainText = plainTextBody(name, "Verify your email address using this link:", verificationUrl);
        sendHtmlEmail(to, subject, htmlContent, plainText);
    }

    public void sendPasswordResetEmail(String to, String name, String resetUrl) {
        String subject = "Password Reset Request";
        String htmlContent = fillTemplate("password-reset.html", name, resetUrl);
        String plainText = plainTextBody(name, "Reset your password using this link:", resetUrl);
        sendHtmlEmail(to, subject, htmlContent, plainText);
    }

    /**
     * Substitutes the placeholders with literal {@code String.replace}, not {@code replaceAll}:
     * the name is user-controlled and a {@code $} or {@code \} in it is a regex replacement
     * escape that throws or corrupts the output. Both values are HTML-escaped because they land
     * in markup: the name in text, the URL in attributes and text (so {@code &} becomes
     * {@code &amp;}, which is what a correct href needs).
     */
    private String fillTemplate(String templateName, String name, String url) {
        return loadEmailTemplate(templateName)
                .replace("{{USER_NAME}}", HtmlUtils.htmlEscape(name))
                .replace("{{ACTIVATE_URL}}", HtmlUtils.htmlEscape(url));
    }

    /**
     * Plain-text body for the fallback send. Built directly rather than by stripping tags from
     * the HTML, which left the stylesheet text in and lost the link that is the whole point of
     * the message.
     */
    private String plainTextBody(String name, String instruction, String url) {
        return "Assalamu alaikum, " + name + "\n\n" + instruction + "\n" + url + "\n";
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent, String plainTextFallback) {
        try {
            logger.info("Attempting to send HTML email to: {} with subject: {}", to, subject);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set from address with name
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("HTML email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send HTML email to: {} - Error: {}", to, e.getMessage(), e);
            logger.info("Attempting fallback to plain text email for: {}", to);
            try {
                // Fallback to plain text
                sendEmail(to, subject, plainTextFallback);
            } catch (Exception fallbackError) {
                logger.error("Fallback plain text email also failed for: {} - Error: {}", to,
                        fallbackError.getMessage(), fallbackError);
                throw new RuntimeException("Failed to send email (HTML and plain text fallback)", fallbackError);
            }
        }
    }

    private String loadEmailTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + templateName);
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Failed to load email template: {} - Error: {}", templateName, e.getMessage(), e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }
}
