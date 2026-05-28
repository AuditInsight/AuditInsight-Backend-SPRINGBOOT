package com.diana.auditinsightbackendspringboot.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${BREVO_API}")
    private String brevoApiKey;

    @Value("${BREVO_MAIL}")
    private String fromEmail;

    @Value("${BREVO_SENDER_NAME:AuditInsight}")
    private String fromName;

    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("sender", Map.of("email", fromEmail, "name", fromName));
            payload.put("to", List.of(Map.of("email", to)));
            payload.put("subject", subject);
            payload.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BREVO_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent to {}", to);
            } else {
                log.error("Brevo error: {}", response.getBody());
            }

        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
        }

        // BUG FIX: removed System.out.println(brevoApiKey) — this was leaking your API key
        // to stdout/logs in every environment including production.
    }

    public void sendVerificationEmail(String email, String name, String otp) {
        if (email == null || name == null) {
            log.error("Cannot send verification email: missing email or name");
            return;
        }

        String html = String.format("""
                <html>
                <body style='font-family: Arial, sans-serif;'>
                    <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                        <h2 style='color:#4CAF50;'>Email Verification</h2>
                        <p>Hello <strong>%s</strong>,</p>
                        <p>Your One-Time Password (OTP) for account verification is:</p>
                        <div style='text-align:center;margin:20px;'>
                            <span style='font-size:2em;letter-spacing:8px;background:#f4f4f4;padding:10px 20px;border-radius:5px;border:1px solid #ccc;'>%s</span>
                        </div>
                        <p>Enter this OTP in the app to verify your account. This code expires in 10 minutes.</p>
                        <small>If you did not request this, please ignore this email.</small>
                    </div>
                </body>
                </html>""", name, otp);

        sendEmail(email, "Your OTP for AuditInsight Account Verification", html);
    }

    public void sendConfirmationEmail(String email, String name) {
        if (email == null || name == null) {
            log.error("Cannot send confirmation email: missing email or name");
            return;
        }

        String html = String.format("""
                <html>
                  <body>
                      <p>It is a pleasure to have you working with <b>AuditInsight</b>, <b>%s</b>.</p>
                      <p>AuditInsight will now help you get accurate, fast and transparent audit results.</p>
                      <br>
                      <p>Your account is currently under review. You will be notified once approved so that you can start publishing job vacancies!</p>
                  </body>
                </html>""", name);

        sendEmail(email, "Account Under Review", html);
    }
}
