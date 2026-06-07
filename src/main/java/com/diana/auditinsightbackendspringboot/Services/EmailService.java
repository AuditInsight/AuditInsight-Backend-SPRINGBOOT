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

    public void sendApprovalEmail(String email, String name) {
        if (email == null || name == null) {
            log.error("Cannot send approval email: missing email or name");
            return;
        }

        String html = String.format("""
                <html>
                  <body style='font-family: Arial, sans-serif;'>
                      <p>Hello <b>%s</b>,</p>
                      <p>Great news! Your <b>AuditInsight</b> auditor account has been reviewed and approved.</p>
                      <p>You can now log in and start working on auditing engagements.</p>
                      <br>
                      <p>Welcome aboard!</p>
                  </body>
                </html>""", name);

        sendEmail(email, "Your AuditInsight Account Has Been Approved", html);
    }

    public void sendInvitationEmail(String email, String orgName, String token) {
        if (email == null || orgName == null) {
            log.error("Cannot send invitation email: missing email or orgName");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2>You've been invited to join <b>%s</b> on AuditInsight</h2>
                    <p>Click the link below to register and join the organisation:</p>
                    <p><a href='http://localhost:8080/register?inviteToken=%s'
                          style='background:#4CAF50;color:#fff;padding:10px 20px;
                                 text-decoration:none;border-radius:4px;'>
                      Accept Invitation
                    </a></p>
                    <p>This invitation expires in 72 hours.</p>
                    <small>If you did not expect this, please ignore this email.</small>
                  </div>
                </body></html>""", orgName, token);
        sendEmail(email, "You're invited to join " + orgName + " on AuditInsight", html);
    }

    public void sendAddedToOrgEmail(String email, String name, String orgName, String role) {
        if (email == null || orgName == null) {
            log.error("Cannot send org-added email: missing email or orgName");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2>You've been added to <b>%s</b></h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>You have been added to the organisation <b>%s</b> on AuditInsight
                       as a <b>%s</b>.</p>
                    <p>Log in to AuditInsight to get started.</p>
                  </div>
                </body></html>""", orgName, name, orgName, role);
        sendEmail(email, "You've been added to " + orgName + " on AuditInsight", html);
    }

    public void sendMemberCredentialsEmail(String email, String defaultPassword,
                                           String orgName, String role, String token) {
        if (email == null || orgName == null) {
            log.error("Cannot send credentials email: missing email or orgName");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2>You have been invited to join <b>%s</b> on AuditInsight</h2>
                    <p>Your account has been created. Use the credentials below to log in and activate your membership:</p>
                    <table style='margin:20px 0;border-collapse:collapse;'>
                      <tr>
                        <td style='padding:8px 16px 8px 0;font-weight:bold;'>Username (email):</td>
                        <td style='padding:8px;background:#f4f4f4;border-radius:4px;'>%s</td>
                      </tr>
                      <tr>
                        <td style='padding:8px 16px 8px 0;font-weight:bold;'>Temporary password:</td>
                        <td style='padding:8px;background:#f4f4f4;border-radius:4px;letter-spacing:2px;'>%s</td>
                      </tr>
                      <tr>
                        <td style='padding:8px 16px 8px 0;font-weight:bold;'>Your role:</td>
                        <td style='padding:8px;background:#f4f4f4;border-radius:4px;'>%s</td>
                      </tr>
                      <tr>
                        <td style='padding:8px 16px 8px 0;font-weight:bold;'>Invitation token:</td>
                        <td style='padding:8px;background:#f4f4f4;border-radius:4px;word-break:break-all;font-family:monospace;'>%s</td>
                      </tr>
                    </table>
                    <p>Log in with the credentials above and enter the invitation token when prompted. This token expires in <b>72 hours</b>.</p>
                    <p style='color:#e74c3c;font-weight:bold;'>
                      You will be required to change your password after your first login.
                    </p>
                    <small>If you did not expect this invitation, please contact your organisation administrator.</small>
                  </div>
                </body></html>""", orgName, email, defaultPassword, role, token);
        sendEmail(email, "Your AuditInsight account for " + orgName, html);
    }

    public void sendExistingUserInvitationEmail(String email, String name,
                                                String orgName, String role, String token) {
        if (email == null || orgName == null) {
            log.error("Cannot send invitation email: missing email or orgName");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2>You've been invited to join <b>%s</b> on AuditInsight</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>You have been invited to collaborate on <b>%s</b> as a <b>%s</b>.</p>
                    <p>Use the invitation token below when logging in to activate your membership. This token expires in <b>72 hours</b>.</p>
                    <div style='text-align:center;margin:20px;'>
                      <span style='font-family:monospace;font-size:0.9em;background:#f4f4f4;padding:12px 20px;
                                   border-radius:5px;border:1px solid #ccc;word-break:break-all;display:inline-block;'>%s</span>
                    </div>
                    <small>If you did not expect this, please ignore this email.</small>
                  </div>
                </body></html>""", orgName, name, orgName, role, token);
        sendEmail(email, "You've been invited to join " + orgName + " on AuditInsight", html);
    }

    public void sendPasswordChangedEmail(String email, String name) {
        if (email == null) {
            log.error("Cannot send password changed email: missing email");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#4CAF50;'>Password Changed Successfully</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>Your AuditInsight account password has been changed successfully.</p>
                    <p>If you did not make this change, please contact support immediately.</p>
                  </div>
                </body></html>""", name != null ? name : email);
        sendEmail(email, "Your AuditInsight password has been changed", html);
    }

    public void sendOwnershipTransferEmail(String email, String name, String orgName, String newRole) {
        if (email == null || orgName == null) {
            log.error("Cannot send ownership transfer email: missing email or orgName");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2>Your role in <b>%s</b> has been updated</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>Your role in the organisation <b>%s</b> on AuditInsight has been updated to <b>%s</b>.</p>
                    <p>Log in to AuditInsight to continue with your updated access level.</p>
                  </div>
                </body></html>""", orgName, name != null ? name : email, orgName, newRole);
        sendEmail(email, "Your role in " + orgName + " has been updated", html);
    }

    public void sendPasswordChangeReminderEmail(String email, String name) {
        if (email == null) {
            log.error("Cannot send password change reminder: missing email");
            return;
        }
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#e74c3c;'>Action Required: Change Your Password</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>Your account has been activated. For security reasons, you must change your
                       temporary password before you can access your organisation.</p>
                    <p>Please use the <b>Change Password</b> option in AuditInsight to set your new password.</p>
                    <p>Your new password must contain at least 8 characters including uppercase,
                       lowercase, a number, and a special character.</p>
                    <small>If you did not expect this, please contact your organisation administrator.</small>
                  </div>
                </body></html>""", name != null ? name : email);
        sendEmail(email, "Action Required: Change your AuditInsight password", html);
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
