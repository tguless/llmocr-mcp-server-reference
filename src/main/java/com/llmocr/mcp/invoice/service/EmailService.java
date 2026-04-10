package com.llmocr.mcp.invoice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;

    @Value("${app.email.from:noreply@eyesense.ai}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:3001}")
    private String baseUrl;

    /**
     * Check if email notifications are enabled
     * 
     * @return true if SendGrid is properly configured and emails can be sent
     */
    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    /**
     * Send a simple text email
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        if (!emailEnabled) {
            log.warn("Email notifications are DISABLED - SendGrid API key not configured. Cannot send email to: {}", to);
            throw new RuntimeException("Email notifications are disabled. Please configure SendGrid API key.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send HTML email
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (!emailEnabled) {
            log.warn("Email notifications are DISABLED - SendGrid API key not configured. Cannot send email to: {}", to);
            throw new RuntimeException("Email notifications are disabled. Please configure SendGrid API key.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String to, String userName, String resetToken) {
        String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
        
        String subject = "Password Reset Request";
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                    <h1 style="color: white; margin: 0;">Password Reset Request</h1>
                </div>
                <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;">
                    <p>Hello <strong>%s</strong>,</p>
                    <p>We received a request to reset your password. Click the button below to create a new password:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block; font-weight: bold;">Reset Password</a>
                    </div>
                    <p>Or copy and paste this link in your browser:</p>
                    <p style="background: white; padding: 10px; border-radius: 5px; word-break: break-all; font-size: 12px;">%s</p>
                    <p><strong>This link will expire in 1 hour.</strong></p>
                    <p>If you didn't request a password reset, please ignore this email or contact support if you have concerns.</p>
                    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">
                    <p style="font-size: 12px; color: #666; text-align: center;">
                        This is an automated email. Please do not reply to this message.
                    </p>
                </div>
            </body>
            </html>
            """, userName, resetUrl, resetUrl);
        
        sendHtmlEmail(to, subject, htmlContent);
        log.info("Password reset email sent to: {}", to);
    }

    /**
     * Send password changed confirmation email
     */
    public void sendPasswordChangedEmail(String to, String userName) {
        String subject = "Password Changed Successfully";
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                    <h1 style="color: white; margin: 0;">Password Changed</h1>
                </div>
                <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;">
                    <p>Hello <strong>%s</strong>,</p>
                    <p>This is to confirm that your password has been successfully changed.</p>
                    <p>If you did not make this change, please contact our support team immediately.</p>
                    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">
                    <p style="font-size: 12px; color: #666; text-align: center;">
                        This is an automated email. Please do not reply to this message.
                    </p>
                </div>
            </body>
            </html>
            """, userName);
        
        sendHtmlEmail(to, subject, htmlContent);
        log.info("Password changed confirmation email sent to: {}", to);
    }
}

