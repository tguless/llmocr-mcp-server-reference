package com.llmocr.mcp.invoice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Email configuration
 * 
 * Conditionally enables email based on whether SendGrid API key is configured.
 * Email will ONLY be enabled if SENDGRID_API_KEY environment variable is set
 * to a non-placeholder value.
 */
@Configuration
@Slf4j
public class EmailConfig {

    @Value("${spring.mail.password:}")
    private String sendGridApiKey;

    @Value("${spring.mail.host:smtp.sendgrid.net}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${spring.mail.username:apikey}")
    private String mailUsername;

    /**
     * Check if email is properly configured
     * 
     * Email is considered configured if:
     * 1. SendGrid API key is set
     * 2. It's not the placeholder value
     * 3. It's not empty
     */
    @Bean
    public boolean emailEnabled() {
        boolean enabled = sendGridApiKey != null 
                && !sendGridApiKey.isEmpty() 
                && !sendGridApiKey.equals("your-sendgrid-api-key-here")
                && sendGridApiKey.length() > 10; // SendGrid keys are long

        if (enabled) {
            log.info("✅ Email notifications ENABLED - SendGrid configured");
        } else {
            log.warn("⚠️  Email notifications DISABLED - SendGrid API key not configured");
            log.warn("    Set SENDGRID_API_KEY environment variable to enable email");
        }

        return enabled;
    }

    /**
     * Override Spring Boot's default JavaMailSender to ensure proper configuration
     * 
     * This bean is only created if email is enabled (SendGrid API key is configured)
     */
    @Bean
    @ConditionalOnProperty(name = "spring.mail.password")
    public JavaMailSender javaMailSender() {
        // Only log if we're actually creating the bean
        if (!emailEnabled()) {
            log.warn("Skipping JavaMailSender bean creation - email not configured");
            return null;
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(sendGridApiKey);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        log.info("JavaMailSender configured with host: {}, port: {}", mailHost, mailPort);
        return mailSender;
    }
}

