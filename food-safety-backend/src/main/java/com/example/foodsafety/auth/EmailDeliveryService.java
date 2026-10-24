package com.example.foodsafety.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailDeliveryService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String publicBaseUrl;
    private final String smtpHost;

    public EmailDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.email-from:}") String from,
            @Value("${app.auth.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${spring.mail.host:}") String smtpHost
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.smtpHost = smtpHost;
    }

    public void sendVerification(String email, String token) {
        String link = publicBaseUrl + "/api/auth/verify-email?token=" + token;
        send(email, "Verify your Bitewise account",
                "Verify your email by opening this link:\n\n" + link
                        + "\n\nThis link expires in 24 hours. If you did not create this account, ignore this email.");
    }

    public void sendPasswordReset(String email, String token) {
        send(email, "Reset your Bitewise password",
                "Use this one-time token with POST /api/auth/password/reset:\n\n"
                        + "{\"token\":\"" + token + "\",\"newPassword\":\"your-new-password\"}"
                        + "\n\nThis token expires in 30 minutes. If you did not request this, ignore this email.");
    }

    private void send(String recipient, String subject, String body) {
        if (mailSender == null || smtpHost.isBlank() || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured. Set SMTP and AUTH_EMAIL_FROM environment variables before registering.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery failed. Try again later.", ex);
        }
    }
}
