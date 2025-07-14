package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@lensbridge.tech}")
    private String fromAddress;

    /**
     * Sends a simple email.
     * @param to the recipient's email address
     * @param subject the subject of the email
     * @param text the body of the email
     */
    public void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
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
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Fallback to plain text
            sendEmail(to, subject, htmlContent.replaceAll("<[^>]*>", ""));
        }
    }

    // Generate a random token for verification or password reset
    public String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
