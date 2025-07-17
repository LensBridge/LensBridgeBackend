package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
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
     * @param to the recipient's email address
     * @param subject the subject of the email
     * @param text the body of the email
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
     * @param to
     * @param verificationUrl
     */
    public void sendVerificationEmail(String to, String verificationUrl) {
        String subject = "Verify Your Email Address";
        String htmlContent = String.format(
            "<h2>Welcome!</h2>" +
            "<p>Please click the button below to verify your email address:</p>" +
            "<a href='%s' style='background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;'>Verify Email</a>" +
            "<p>If the button doesn't work, copy and paste this link into your browser:</p>" +
            "<p>%s</p>" +
            "<p>This link will expire in 24 hours.</p>",
            verificationUrl, verificationUrl
        );
        sendHtmlEmail(to, subject, htmlContent);
    }

    public void sendPasswordResetEmail(String to, String resetUrl) {
        String subject = "Password Reset Request";
        String htmlContent = String.format(
            "<h2>Password Reset Request</h2>" +
            "<p>Click the button below to reset your password:</p>" +
            "<a href='%s' style='background-color: #dc3545; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;'>Reset Password</a>" +
            "<p>If the button doesn't work, copy and paste this link into your browser:</p>" +
            "<p>%s</p>" +
            "<p>If you didn't request this, please ignore this email.</p>" +
            "<p>This link will expire in 24 hours.</p>",
            resetUrl, resetUrl
        );
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
                logger.error("Fallback plain text email also failed for: {} - Error: {}", to, fallbackError.getMessage(), fallbackError);
                throw new RuntimeException("Failed to send email (HTML and plain text fallback)", fallbackError);
            }
        }
    }

    // Generate a random token for verification or password reset
    public String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
