package com.aivle26.aipm.Service;

import com.aivle26.aipm.Exception.ApiException;
import jakarta.mail.MessagingException;
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

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from-address:noreply@aipm.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:AIPM}")
    private String fromName;

    public void sendVerificationCode(String email, String code) {
        sendMail(email, "AIPM email verification code", "Your verification code is " + code + ". It expires in 5 minutes.");
    }

    public void sendLoginVerificationCode(String email, String code) {
        sendMail(
                email,
                "[BidWorks AI] 로그인 인증번호 안내",
                """
                        BidWorks AI 로그인 인증번호는 발급된 6자리 번호입니다.
                        인증번호는 3분 동안 유효합니다.
                        본인이 요청하지 않은 경우 이 메일을 무시해 주세요.

                        인증번호: %s
                        """.formatted(code)
        );
    }

    private void sendMail(String email, String subject, String text) {
        validateMailConfiguration();

        try {
            var message = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(email);
            helper.setFrom(resolveFromInternetAddress());
            helper.setSubject(subject);
            helper.setText(text);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            log.warn("Mail message creation failed. type={}, causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "mail message creation failed", exception);
        } catch (MailAuthenticationException exception) {
            log.warn("Mail authentication failed. type={}, causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mail authentication failed", exception);
        } catch (MailException exception) {
            log.warn("Mail delivery failed. type={}, causeType={}, cause={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName(),
                    sanitize(exception.getMessage()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mail delivery failed", exception);
        }
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress.trim();
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail from address not configured");
    }

    private InternetAddress resolveFromInternetAddress() throws UnsupportedEncodingException, AddressException {
        String resolvedFromAddress = resolveFromAddress();
        if (StringUtils.hasText(fromName)) {
            return new InternetAddress(resolvedFromAddress, fromName.trim(), "UTF-8");
        }
        return new InternetAddress(resolvedFromAddress);
    }

    private void validateMailConfiguration() {
        if (!StringUtils.hasText(mailUsername)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail username not configured");
        }
        if (!StringUtils.hasText(mailPassword)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail password not configured");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail from address not configured");
        }
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[redacted-email]")
                .replaceAll("(?i)(password|passwd|app password)[^\\s]*", "[redacted-secret]");
    }
}
