package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
     * @param to
     * @param verificationUrl
     */
    public void sendWelcomeEmail(String to, String name) {
        String subject = "Verify Your Email Address";
        String htmlContent = loadEmailTemplate("email-verification.html");
        htmlContent = htmlContent.replaceAll("\\{\\{USER_NAME\\}\\}", name);
        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * Sends a verification email with a link.
     * 
     * @param to
     * @param verificationUrl
     */
    public void sendVerificationEmail(String to, String name, String verificationUrl) {
        String subject = "Verify Your Email Address";
        String htmlContent = loadEmailTemplate("email-verification.html");
        htmlContent = htmlContent.replaceAll("\\{\\{USER_NAME\\}\\}", name)
                .replaceAll("\\{\\{ACTIVATE_URL\\}\\}", verificationUrl);
        sendHtmlEmail(to, subject, htmlContent);
    }

    public void sendPasswordResetEmail(String to, String name, String resetUrl) {
        String subject = "Password Reset Request";
        String htmlContent = loadEmailTemplate("password-reset.html");
        htmlContent = htmlContent
            .replaceAll("\\{\\{ACTIVATE_URL\\}\\}", resetUrl)
            .replaceAll("\\{\\{USER_NAME\\}\\}", name);
        sendHtmlEmail(to, subject, htmlContent);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
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
                String plainText = htmlContent.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                sendEmail(to, subject, plainText);
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
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to load email template: {} - Error: {}", templateName, e.getMessage(), e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }

    // Generate a random token for verification or password reset
    public String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
