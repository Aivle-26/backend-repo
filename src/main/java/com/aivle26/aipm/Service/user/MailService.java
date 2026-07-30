package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Exception.ApiException;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {
    private static final int MAX_ALLOWED_ATTEMPTS = 5;
    private static final long MAX_ALLOWED_RETRY_DELAY_MS = 10_000;

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from-address:}")
    private String fromAddress;

    @Value("${app.mail.from-name:BidWorks AI}")
    private String fromName;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.subject-prefix:[BidWorks AI]}")
    private String subjectPrefix;

    @Value("${app.mail.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.mail.retry-delay-ms:500}")
    private long retryDelayMs;

    @PostConstruct
    void validateStartupConfiguration() {
        if (!enabled) {
            log.info("Transactional email delivery is disabled");
            return;
        }
        String missingSetting = findMissingMailSetting();
        if (missingSetting != null) {
            throw new IllegalStateException(
                    "Transactional email configuration is missing required setting: " + missingSetting
            );
        }
        validateSenderAddresses();
        if (maxAttempts < 1 || maxAttempts > MAX_ALLOWED_ATTEMPTS) {
            throw new IllegalStateException("app.mail.max-attempts must be between 1 and " + MAX_ALLOWED_ATTEMPTS);
        }
        if (retryDelayMs < 0 || retryDelayMs > MAX_ALLOWED_RETRY_DELAY_MS) {
            throw new IllegalStateException("app.mail.retry-delay-ms must be between 0 and " + MAX_ALLOWED_RETRY_DELAY_MS);
        }
    }

    // 회원가입 이메일 소유권 확인용 인증번호를 전송한다.
    public void sendSignupVerificationCode(String email, String code) {
        sendVerificationMail(
                email,
                "회원가입 인증번호 안내",
                "BidWorks AI 회원가입 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                code,
                5
        );
    }

    // 비밀번호 재설정용 이메일 인증번호를 전송한다.
    public void sendPasswordResetVerificationCode(String email, String code) {
        sendVerificationMail(
                email,
                "비밀번호 재설정 인증번호 안내",
                "BidWorks AI 비밀번호 재설정 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                code,
                5
        );
    }

    // 수신 이메일과 인증 코드로 로그인 확인 안내 메일을 전송한다.
    public void sendLoginVerificationCode(String email, String code) {
        sendVerificationMail(
                email,
                "로그인 인증번호 안내",
                "BidWorks AI 로그인 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                code,
                3
        );
    }

    private void sendVerificationMail(String email, String subject, String description, String code, int expiresInMinutes) {
        String resolvedSubject = resolveSubject(subject);
        String plainText = buildVerificationText(resolvedSubject, description, code, expiresInMinutes);
        String htmlText = buildVerificationHtml(description, code, expiresInMinutes);

        sendMail(email, resolvedSubject, plainText, htmlText);
    }

    private String buildVerificationText(
            String subject,
            String description,
            String verificationCode,
            int expiresInMinutes
    ) {
        return """
                %s

                %s

                인증번호: %s
                인증번호는 %d분 동안 유효합니다.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                본 메일은 회신할 수 없는 자동 발송 메일입니다.

                BidWorks AI 운영팀
                """.formatted(subject, description, verificationCode, expiresInMinutes);
    }

    private String buildVerificationHtml(String description, String verificationCode, int expiresInMinutes) {
        String escapedDescription = HtmlUtils.htmlEscape(description);
        String escapedVerificationCode = HtmlUtils.htmlEscape(verificationCode);
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>BidWorks AI 이메일 인증</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f6f8;color:#0f172a;font-family:Arial,'Noto Sans KR',sans-serif">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#f4f6f8;border-collapse:collapse">
                    <tr>
                      <td align="center" style="padding:24px">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:560px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;border-collapse:separate">
                          <tr>
                            <td style="padding:32px">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                <tr>
                                  <td align="left" style="padding:0 0 20px;font-size:22px;font-weight:700;line-height:1.3;color:#172554">
                                    BidWorks AI
                                  </td>
                                </tr>
                                <tr>
                                  <td align="left" style="padding:0 0 24px;font-size:16px;line-height:1.6;color:#0f172a">
                                    %s
                                  </td>
                                </tr>
                                <tr>
                                  <td align="center" style="padding:20px;background-color:#f8fafc;border-radius:8px">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                      <tr>
                                        <td align="center" style="padding:0 0 10px;font-size:13px;line-height:1.4;color:#64748b">
                                          인증번호
                                        </td>
                                      </tr>
                                      <tr>
                                        <td align="center" style="padding:0;font-size:32px;font-weight:700;line-height:1.2;color:#172554">
                                          <span style="display:inline-block;padding-left:8px;letter-spacing:8px">%s</span>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                                <tr>
                                  <td align="left" style="padding:20px 0 8px;font-size:14px;line-height:1.5;color:#0f172a">
                                    인증번호는 <strong>%d분</strong> 동안 유효합니다.
                                  </td>
                                </tr>
                                <tr>
                                  <td align="left" style="padding:0;font-size:13px;line-height:1.5;color:#64748b">
                                    본인이 요청하지 않았다면 이 메일을 무시해 주세요.<br>
                                    본 메일은 회신할 수 없는 자동 발송 메일입니다.
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:24px 0 0">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse;border-top:1px solid #e2e8f0">
                                      <tr>
                                        <td align="left" style="padding:20px 0 0;font-size:12px;line-height:1.6;color:#64748b">
                                          <strong style="color:#475569">BidWorks AI 운영팀</strong>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escapedDescription, escapedVerificationCode, expiresInMinutes);
    }

    // 메일 설정을 검증하고 일시적인 SMTP 장애에 한해 제한적으로 재시도한다.
    private void sendMail(String email, String subject, String plainText, String htmlText) {
        validateMailConfiguration();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var message = javaMailSender.createMimeMessage();
                var helper = new MimeMessageHelper(
                        message,
                        StandardCharsets.UTF_8.name()
                );
                helper.setTo(email);
                helper.setFrom(resolveFromInternetAddress());
                if (StringUtils.hasText(replyTo)) {
                    helper.setReplyTo(replyTo.trim());
                }
                helper.setSubject(subject);
                setAlternativeBody(message, plainText, htmlText);
                javaMailSender.send(message);
                return;
            } catch (MessagingException | UnsupportedEncodingException exception) {
                log.warn("Mail message creation failed. type={}, causeType={}",
                        exception.getClass().getSimpleName(),
                        exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_MESSAGE_CREATION_FAILED",
                        "mail message creation failed", exception);
            } catch (MailAuthenticationException exception) {
                log.warn("Mail delivery failed. category=SMTP_AUTHENTICATION, type={}, causeType={}",
                        exception.getClass().getSimpleName(),
                        exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_AUTHENTICATION_FAILED",
                        "mail authentication failed", exception);
            } catch (MailException exception) {
                MailDeliveryFailure failure = classifyDeliveryFailure(exception, email);
                if (!failure.retryable || attempt >= maxAttempts) {
                    log.warn("Mail delivery failed. category={}, attempt={}/{}, type={}, causeType={}",
                            failure,
                            attempt,
                            maxAttempts,
                            exception.getClass().getSimpleName(),
                            exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
                    if (failure == MailDeliveryFailure.SMTP_AUTHENTICATION) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_AUTHENTICATION_FAILED",
                                "mail authentication failed", exception);
                    }
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_DELIVERY_FAILED",
                            "mail delivery failed", exception);
                }
                log.info("Retrying mail delivery after transient failure. category={}, attempt={}/{}",
                        failure, attempt, maxAttempts);
                waitBeforeRetry();
            }
        }
    }

    private void setAlternativeBody(MimeMessage message, String plainText, String htmlText) throws MessagingException {
        MimeBodyPart plainTextPart = new MimeBodyPart();
        plainTextPart.setText(plainText, StandardCharsets.UTF_8.name(), "plain");

        MimeBodyPart htmlTextPart = new MimeBodyPart();
        htmlTextPart.setText(htmlText, StandardCharsets.UTF_8.name(), "html");

        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(plainTextPart);
        alternative.addBodyPart(htmlTextPart);
        message.setContent(alternative);
    }

    private String resolveSubject(String subject) {
        if (!StringUtils.hasText(subjectPrefix)) {
            return subject;
        }
        String prefix = subjectPrefix.trim();
        return subject.startsWith(prefix) ? subject : prefix + " " + subject;
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress.trim();
        }
        throw unavailable();
    }

    // 설정된 발신 주소와 표시명을 MIME 호환 인터넷 주소로 변환한다.
    private InternetAddress resolveFromInternetAddress() throws UnsupportedEncodingException, AddressException {
        String resolvedFromAddress = resolveFromAddress();
        if (StringUtils.hasText(fromName)) {
            return new InternetAddress(resolvedFromAddress, fromName.trim(), "UTF-8");
        }
        return new InternetAddress(resolvedFromAddress);
    }

    // SMTP 인증에 필요한 호스트·사용자명·비밀번호 설정이 존재하는지 검증한다.
    private void validateMailConfiguration() {
        if (!enabled) {
            throw unavailable();
        }
        String missingSetting = findMissingMailSetting();
        if (missingSetting != null) {
            log.error("Transactional email configuration is missing required setting: {}", missingSetting);
            throw unavailable();
        }
        validateSenderAddresses();
    }

    private String findMissingMailSetting() {
        if (!StringUtils.hasText(mailHost)) {
            return "MAIL_HOST";
        }
        if (!StringUtils.hasText(mailUsername)) {
            return "MAIL_USERNAME";
        }
        if (!StringUtils.hasText(mailPassword)) {
            return "MAIL_PASSWORD";
        }
        if (!StringUtils.hasText(fromAddress)) {
            return "MAIL_FROM_ADDRESS";
        }
        return null;
    }

    private void validateSenderAddresses() {
        try {
            new InternetAddress(fromAddress.trim(), true);
            if (StringUtils.hasText(replyTo)) {
                new InternetAddress(replyTo.trim(), true);
            }
        } catch (AddressException exception) {
            throw new IllegalStateException("app.mail sender address is invalid", exception);
        }
    }

    private MailDeliveryFailure classifyDeliveryFailure(Throwable exception, String recipient) {
        String failureText = collectFailureText(exception);
        String normalizedFromAddress = normalize(fromAddress);
        String normalizedRecipient = normalize(recipient);

        if (containsAny(failureText, "authentication credentials invalid", "authentication failed", "smtp 535", " 535 ")) {
            return MailDeliveryFailure.SMTP_AUTHENTICATION;
        }

        boolean identityNotVerified = containsAny(
                failureText,
                "identity is not verified",
                "identity failed the check",
                "email address is not verified",
                "address is not verified",
                "mail from domain is not verified"
        );
        if (failureText.contains("sandbox")) {
            return MailDeliveryFailure.SES_SANDBOX_RESTRICTION;
        }
        if (identityNotVerified && StringUtils.hasText(normalizedFromAddress)
                && failureText.contains(normalizedFromAddress)) {
            return MailDeliveryFailure.SES_SENDER_IDENTITY_NOT_VERIFIED;
        }
        if (identityNotVerified
                && StringUtils.hasText(normalizedRecipient)
                && failureText.contains(normalizedRecipient)) {
            return MailDeliveryFailure.SES_RECIPIENT_IDENTITY_NOT_VERIFIED;
        }
        if (identityNotVerified) {
            return MailDeliveryFailure.SES_IDENTITY_NOT_VERIFIED;
        }
        if (hasNetworkCause(exception) || containsAny(
                failureText,
                "could not connect",
                "connection refused",
                "connection timed out",
                "connect timed out",
                "read timed out",
                "unknown host"
        )) {
            return MailDeliveryFailure.NETWORK;
        }
        return MailDeliveryFailure.SMTP_DELIVERY;
    }

    private String collectFailureText(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (StringUtils.hasText(current.getMessage())) {
                result.append(' ').append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }
        return result.toString();
    }

    private boolean hasNetworkCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_DELIVERY_INTERRUPTED",
                    "mail delivery interrupted",
                    exception
            );
        }
    }

    private ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MAIL_NOT_CONFIGURED",
                "mail service is not configured"
        );
    }

    private enum MailDeliveryFailure {
        SMTP_AUTHENTICATION(false),
        SES_SENDER_IDENTITY_NOT_VERIFIED(false),
        SES_RECIPIENT_IDENTITY_NOT_VERIFIED(false),
        SES_SANDBOX_RESTRICTION(false),
        SES_IDENTITY_NOT_VERIFIED(false),
        NETWORK(true),
        SMTP_DELIVERY(true);

        private final boolean retryable;

        MailDeliveryFailure(boolean retryable) {
            this.retryable = retryable;
        }
    }
}
