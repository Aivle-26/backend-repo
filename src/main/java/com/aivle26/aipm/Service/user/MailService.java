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
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {
    private static final int MAX_ALLOWED_ATTEMPTS = 5;
    private static final long MAX_ALLOWED_RETRY_DELAY_MS = 10_000;
    private static final String SERVICE_NAME = "PM Agent";
    private static final String REPRESENTATIVE = "TEAM26";
    private static final String COMPANY_ADDRESS = "부산광역시 동구 초량중로 29, 3층";
    private static final String CONTACT_EMAIL = "aivleschool1@gmail.com";

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

    @Value("${app.mail.from-name:PM Agent}")
    private String fromName;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.subject-prefix:[PM Agent]}")
    private String subjectPrefix;

    @Value("${app.mail.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.mail.retry-delay-ms:500}")
    private long retryDelayMs;

    @Value("${app.mail.asset-base-url}")
    private String assetBaseUrl;

    @PostConstruct
    void validateStartupConfiguration() {
        if (!enabled) {
            log.info("Transactional email delivery is disabled");
            return;
        }
        validateMailConfiguration();
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
                "회원가입 이메일 인증",
                "회원가입을 완료하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 회원가입 화면에 입력하시면 인증이 완료됩니다.",
                code,
                5
        );
    }

    // 비밀번호 재설정용 이메일 인증번호를 전송한다.
    public void sendPasswordResetVerificationCode(String email, String code) {
        sendVerificationMail(
                email,
                "비밀번호 재설정 인증",
                "비밀번호 재설정을 완료하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.",
                code,
                5
        );
    }

    // 수신 이메일과 인증 코드로 로그인 확인 안내 메일을 전송한다.
    public void sendLoginVerificationCode(String email, String code) {
        sendVerificationMail(
                email,
                "로그인 인증",
                "로그인을 계속하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 로그인 화면에 입력하시면 인증이 완료됩니다.",
                code,
                3
        );
    }

    private void sendVerificationMail(
            String email,
            String subject,
            String headline,
            String instruction,
            String code,
            int expiresInMinutes
    ) {
        String resolvedSubject = resolveSubject(subject);
        String plainText = buildVerificationText(
                resolvedSubject,
                headline,
                instruction,
                code,
                expiresInMinutes
        );
        String htmlText = buildVerificationHtml(headline, instruction, code, expiresInMinutes);

        sendMail(email, resolvedSubject, plainText, htmlText);
    }

    private String buildVerificationText(
            String subject,
            String headline,
            String instruction,
            String verificationCode,
            int expiresInMinutes
    ) {
        return """
                %s

                %s
                %s

                인증번호: %s
                인증번호는 %d분 동안 유효합니다.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                이 메일은 발신 전용입니다.

                %s
                대표: %s
                주소: %s
                이메일: %s

                본 메일은 발신 전용으로 회신되지 않습니다.
                """.formatted(
                subject,
                headline,
                instruction,
                verificationCode,
                expiresInMinutes,
                SERVICE_NAME,
                REPRESENTATIVE,
                COMPANY_ADDRESS,
                CONTACT_EMAIL
        );
    }

    private String buildVerificationHtml(
            String headline,
            String instruction,
            String verificationCode,
            int expiresInMinutes
    ) {
        String escapedHeadline = HtmlUtils.htmlEscape(headline);
        String escapedInstruction = HtmlUtils.htmlEscape(instruction);
        String escapedVerificationCode = HtmlUtils.htmlEscape(verificationCode);
        String backgroundUrl = mailAssetUrl("verification-background.png");
        String clockUrl = mailAssetUrl("verification-clock.png");
        String shieldUrl = mailAssetUrl("verification-shield.png");
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>PM Agent 이메일 인증</title>
                  <style>
                    @media only screen and (max-width: 620px) {
                      .email-shell { width: 100%% !important; }
                      .main-padding { padding: 38px 22px !important; }
                      .headline { font-size: 27px !important; }
                      .verification-code { font-size: 38px !important; letter-spacing: 10px !important; }
                      .footer-padding { padding: 30px 22px !important; }
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background-color:#eef5ff;color:#0b285f;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#eef5ff;border-collapse:collapse">
                    <tr>
                      <td align="center" style="padding:24px 12px">
                        <table class="email-shell" role="presentation" width="680" cellspacing="0" cellpadding="0" border="0" background="%s" style="width:680px;max-width:680px;background-color:#f3f8ff;background-image:url('%s');background-repeat:no-repeat;background-position:center bottom;background-size:cover;border:1px solid #d7e4f7;border-collapse:separate;box-shadow:0 12px 36px rgba(20,58,112,0.10)">
                          <tr>
                            <td align="center" style="padding:34px 24px;background-color:#ffffff;font-size:38px;font-weight:800;line-height:1.2;letter-spacing:-1px;color:#08265e">
                              PM Agent
                            </td>
                          </tr>
                          <tr>
                            <td class="main-padding" style="padding:52px 48px 46px;background-color:rgba(243,248,255,0.86)">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                <tr>
                                  <td class="headline" align="center" style="padding:0 0 18px;font-size:34px;font-weight:800;line-height:1.35;letter-spacing:-1.2px;color:#0b285f">
                                    %s
                                  </td>
                                </tr>
                                <tr>
                                  <td align="center" style="padding:0 0 32px;font-size:16px;line-height:1.7;color:#2e4772">
                                    %s
                                  </td>
                                </tr>
                                <tr>
                                  <td align="center" style="padding:26px 24px;background-color:#ffffff;border:1px solid #e5edf9;border-radius:16px;box-shadow:0 8px 22px rgba(27,70,132,0.07)">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                      <tr>
                                        <td align="center" style="padding:0 0 13px;font-size:17px;line-height:1.4;color:#19386d">
                                          인증번호
                                        </td>
                                      </tr>
                                      <tr>
                                        <td class="verification-code" align="center" style="padding:0;font-size:46px;font-weight:700;line-height:1.2;letter-spacing:14px;color:#08265e">
                                          %s
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:28px 0 0">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                      <tr>
                                        <td width="58" valign="middle" style="width:58px;padding:0 0 18px">
                                          <img src="%s" width="46" height="46" alt="유효시간" style="display:block;width:46px;height:46px;border:0">
                                        </td>
                                        <td valign="middle" style="padding:0 0 18px 4px;font-size:16px;line-height:1.6;color:#173768">
                                          인증번호는 <strong style="color:#1466d9">%d분</strong> 동안 유효합니다.
                                        </td>
                                      </tr>
                                      <tr>
                                        <td colspan="2" style="height:1px;background-color:#cbdcf3;font-size:0;line-height:0">&nbsp;</td>
                                      </tr>
                                      <tr>
                                        <td width="58" valign="top" style="width:58px;padding:22px 0 0">
                                          <img src="%s" width="46" height="46" alt="보안 안내" style="display:block;width:46px;height:46px;border:0">
                                        </td>
                                        <td valign="top" style="padding:22px 0 0 4px;font-size:15px;line-height:1.7;color:#173768">
                                          본인이 요청하지 않았다면 이 메일을 무시해 주세요.<br>
                                          이 메일은 발신 전용입니다.
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="footer-padding" style="padding:34px 48px 46px;background-color:rgba(249,251,255,0.76);border-top:1px solid #cdddf2">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse">
                                <tr>
                                  <td valign="top" style="font-size:13px;line-height:1.75;color:#344b70">
                                    <strong style="font-size:17px;color:#0b285f">%s</strong><br>
                                    대표: %s<br>
                                    주소: %s<br>
                                    이메일: <a href="mailto:%s" style="color:#344b70;text-decoration:none">%s</a><br><br>
                                    본 메일은 발신 전용으로 회신되지 않습니다.
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
                """.formatted(
                backgroundUrl,
                backgroundUrl,
                escapedHeadline,
                escapedInstruction,
                escapedVerificationCode,
                clockUrl,
                expiresInMinutes,
                shieldUrl,
                SERVICE_NAME,
                REPRESENTATIVE,
                COMPANY_ADDRESS,
                CONTACT_EMAIL,
                CONTACT_EMAIL
        );
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
                message.setHeader("Auto-Submitted", "auto-generated");
                message.setHeader("X-Auto-Response-Suppress", "All");
                javaMailSender.send(message);
                return;
            } catch (MessagingException | UnsupportedEncodingException exception) {
                log.warn("Mail message creation failed. type={}, causeType={}",
                        exception.getClass().getSimpleName(),
                        exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_MESSAGE_CREATION_FAILED",
                        "mail message creation failed", exception);
            } catch (MailAuthenticationException exception) {
                log.warn("Mail authentication failed. type={}, causeType={}",
                        exception.getClass().getSimpleName(),
                        exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_AUTHENTICATION_FAILED",
                        "mail authentication failed", exception);
            } catch (MailException exception) {
                if (attempt >= maxAttempts) {
                    log.warn("Mail delivery failed after {} attempts. type={}, causeType={}, cause={}",
                            attempt,
                            exception.getClass().getSimpleName(),
                            exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName(),
                            sanitize(exception.getMessage()));
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_DELIVERY_FAILED",
                            "mail delivery failed", exception);
                }
                log.info("Retrying mail delivery after transient failure. attempt={}/{}", attempt, maxAttempts);
                waitBeforeRetry();
            }
        }
    }

    private void setAlternativeBody(
            MimeMessage message,
            String plainText,
            String htmlText
    ) throws MessagingException {
        MimeBodyPart plainTextPart = new MimeBodyPart();
        plainTextPart.setText(plainText, StandardCharsets.UTF_8.name(), "plain");

        MimeBodyPart htmlTextPart = new MimeBodyPart();
        htmlTextPart.setText(htmlText, StandardCharsets.UTF_8.name(), "html");

        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(plainTextPart);
        alternative.addBodyPart(htmlTextPart);
        message.setContent(alternative);
    }

    private String mailAssetUrl(String fileName) {
        String normalizedBaseUrl = assetBaseUrl == null
                ? ""
                : assetBaseUrl.trim().replaceAll("/+$", "");
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            throw new IllegalStateException("app.mail.asset-base-url must not be blank");
        }
        return HtmlUtils.htmlEscape(normalizedBaseUrl + "/" + fileName);
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
        throw unavailable("mail from address not configured");
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
            throw unavailable("mail delivery not enabled");
        }
        if (!StringUtils.hasText(mailHost)) {
            throw unavailable("mail host not configured");
        }
        if (!StringUtils.hasText(mailUsername)) {
            throw unavailable("mail username not configured");
        }
        if (!StringUtils.hasText(mailPassword)) {
            throw unavailable("mail password not configured");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw unavailable("mail from address not configured");
        }
        try {
            new InternetAddress(fromAddress.trim(), true);
            if (StringUtils.hasText(replyTo)) {
                new InternetAddress(replyTo.trim(), true);
            }
        } catch (AddressException exception) {
            throw new IllegalStateException("app.mail sender address is invalid", exception);
        }
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

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_NOT_CONFIGURED", message);
    }

    // 로그에 출력할 메일 설정값에서 줄바꿈 문자를 제거해 반환한다.
    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[redacted-email]")
                .replaceAll("(?i)(password|passwd|app password)[^\\s]*", "[redacted-secret]");
    }
}
