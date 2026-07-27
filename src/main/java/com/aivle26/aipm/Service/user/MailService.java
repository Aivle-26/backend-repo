package com.aivle26.aipm.Service.user;

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

    // 수신 이메일과 인증 코드로 비밀번호 재설정 안내 메일을 전송한다.
    public void sendVerificationCode(String email, String code) {
        sendMail(email, "AIPM email verification code", "Your verification code is " + code + ". It expires in 5 minutes.");
    }

    // 수신 이메일과 인증 코드로 로그인 확인 안내 메일을 전송한다.
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

    // 메일 설정을 검증하고 발신자·수신자·제목·본문을 구성해 SMTP로 전송한다.
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

    // 전용 발신 주소가 없으면 SMTP 사용자명을 발신 주소로 선택해 반환한다.
    private String resolveFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress.trim();
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail from address not configured");
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
