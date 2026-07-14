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

    public void sendAccountActivatedEmail(String email, String name) {
        if (email == null || name == null) {
            log.error("Cannot send account activated email: missing email or name");
            return;
        }

        String html = String.format("""
                <html>
                  <body style='font-family: Arial, sans-serif;'>
                      <p>Hello <b>%s</b>,</p>
                      <p>Your <b>AuditInsight</b> account has been activated by an administrator.</p>
                      <p>You can now log in and use the platform.</p>
                      <br>
                      <p>Welcome aboard!</p>
                  </body>
                </html>""", name);

        sendEmail(email, "Your AuditInsight Account Has Been Activated", html);
    }

    public void sendAccountDeactivatedEmail(String email, String name) {
        if (email == null || name == null) {
            log.error("Cannot send account deactivated email: missing email or name");
            return;
        }

        String html = String.format("""
                <html>
                  <body style='font-family: Arial, sans-serif;'>
                      <p>Hello <b>%s</b>,</p>
                      <p>Your <b>AuditInsight</b> account has been deactivated by an administrator.</p>
                      <p>If you believe this is a mistake, please contact your organisation administrator.</p>
                  </body>
                </html>""", name);

        sendEmail(email, "Your AuditInsight Account Has Been Deactivated", html);
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

    public void sendTransactionCreatedEmail(String email, String clientName, String orgName,
                                            String txnId, String txnName, String creatorName) {
        if (email == null) return;
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#2c3e50;'>New Transaction Recorded</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>A new transaction has been recorded in your organisation <b>%s</b>.</p>
                    <table style='width:100%%;border-collapse:collapse;margin:16px 0;'>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;width:40%%'>Transaction ID</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                      <tr><td style='padding:8px;font-weight:bold;'>Name</td>
                          <td style='padding:8px;'>%s</td></tr>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;'>Created By</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                    </table>
                    <p>Log in to AuditInsight to view the full details.</p>
                    <small>This is an automated notification from AuditInsight.</small>
                  </div>
                </body></html>""", clientName, orgName, txnId, txnName, creatorName);
        sendEmail(email, "New Transaction Recorded in " + orgName, html);
    }

    public void sendEvidenceUploadedEmail(String email, String clientName, String orgName,
                                          String txnId, String documentName, String uploaderName) {
        if (email == null) return;
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#27ae60;'>Evidence Uploaded</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>New evidence has been uploaded in your organisation <b>%s</b>.</p>
                    <table style='width:100%%;border-collapse:collapse;margin:16px 0;'>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;width:40%%'>Transaction ID</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                      <tr><td style='padding:8px;font-weight:bold;'>Document</td>
                          <td style='padding:8px;'>%s</td></tr>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;'>Uploaded By</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                    </table>
                    <p>Log in to AuditInsight to review the uploaded evidence.</p>
                    <small>This is an automated notification from AuditInsight.</small>
                  </div>
                </body></html>""", clientName, orgName, txnId, documentName, uploaderName);
        sendEmail(email, "Evidence Uploaded for Transaction " + txnId, html);
    }

    public void sendIssueFlaggedEmail(String email, String clientName, String orgName,
                                      String txnId, String issueType, String description,
                                      String auditorName) {
        if (email == null) return;
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#e67e22;'>Issue Flagged on Transaction</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>An auditor has flagged an issue on a transaction in your organisation <b>%s</b>.</p>
                    <table style='width:100%%;border-collapse:collapse;margin:16px 0;'>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;width:40%%'>Transaction ID</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                      <tr><td style='padding:8px;font-weight:bold;'>Issue Type</td>
                          <td style='padding:8px;'>%s</td></tr>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;'>Description</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                      <tr><td style='padding:8px;font-weight:bold;'>Flagged By</td>
                          <td style='padding:8px;'>%s</td></tr>
                    </table>
                    <p>Please log in to AuditInsight to review and address this issue.</p>
                    <small>This is an automated notification from AuditInsight.</small>
                  </div>
                </body></html>""", clientName, orgName, txnId, issueType, description, auditorName);
        sendEmail(email, "Issue Flagged on Transaction " + txnId + " — Action Required", html);
    }

    public void sendIssueResolvedEmail(String email, String clientName, String orgName,
                                       String txnId, String resolutionNote, String resolvedByName) {
        if (email == null) return;
        String html = String.format("""
                <html><body style='font-family:Arial,sans-serif;'>
                  <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                    <h2 style='color:#27ae60;'>Issue Resolved</h2>
                    <p>Hello <b>%s</b>,</p>
                    <p>A flagged issue on a transaction in <b>%s</b> has been marked as resolved.</p>
                    <table style='width:100%%;border-collapse:collapse;margin:16px 0;'>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;width:40%%'>Transaction ID</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                      <tr><td style='padding:8px;font-weight:bold;'>Resolution Note</td>
                          <td style='padding:8px;'>%s</td></tr>
                      <tr><td style='padding:8px;background:#f9f9f9;font-weight:bold;'>Resolved By</td>
                          <td style='padding:8px;background:#f9f9f9;'>%s</td></tr>
                    </table>
                    <p>Log in to AuditInsight to view the full audit trail.</p>
                    <small>This is an automated notification from AuditInsight.</small>
                  </div>
                </body></html>""", clientName, orgName, txnId, resolutionNote, resolvedByName);
        sendEmail(email, "Issue Resolved for Transaction " + txnId, html);
    }

    public void sendPasswordResetEmail(String email, String name, String otp) {
        if (email == null) {
            log.error("Cannot send password reset email: missing email");
            return;
        }

        String html = String.format("""
                <html>
                <body style='font-family: Arial, sans-serif;'>
                    <div style='max-width:600px;margin:auto;padding:20px;border:1px solid #ddd;'>
                        <h2 style='color:#e74c3c;'>Password Reset Request</h2>
                        <p>Hello <strong>%s</strong>,</p>
                        <p>We received a request to reset your AuditInsight account password. Use the code below to continue:</p>
                        <div style='text-align:center;margin:20px;'>
                            <span style='font-size:2em;letter-spacing:8px;background:#f4f4f4;padding:10px 20px;border-radius:5px;border:1px solid #ccc;'>%s</span>
                        </div>
                        <p>Enter this code in the app to set a new password. This code expires in 10 minutes.</p>
                        <small>If you did not request this, please ignore this email.</small>
                    </div>
                </body>
                </html>""", name != null ? name : email, otp);

        sendEmail(email, "Your AuditInsight password reset code", html);
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

}
