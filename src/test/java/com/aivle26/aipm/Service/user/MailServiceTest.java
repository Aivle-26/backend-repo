package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Exception.ApiException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    private static final String VERIFICATION_CODE = "526564";

    @Mock
    private JavaMailSender javaMailSender;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(javaMailSender);
        ReflectionTestUtils.setField(mailService, "enabled", true);
        ReflectionTestUtils.setField(mailService, "mailHost", "localhost");
        ReflectionTestUtils.setField(mailService, "mailUsername", "test-user");
        ReflectionTestUtils.setField(mailService, "mailPassword", "test-password");
        ReflectionTestUtils.setField(mailService, "fromAddress", "no-reply@example.com");
        ReflectionTestUtils.setField(mailService, "fromName", "BidWorks AI");
        ReflectionTestUtils.setField(mailService, "replyTo", "");
        ReflectionTestUtils.setField(mailService, "subjectPrefix", "[BidWorks AI]");
        ReflectionTestUtils.setField(mailService, "maxAttempts", 1);
        ReflectionTestUtils.setField(mailService, "retryDelayMs", 0L);
        lenient().when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void signupMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendSignupVerificationCode("signup@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[BidWorks AI] 회원가입 인증번호 안내",
                "BidWorks AI 회원가입 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                5
        );
    }

    @Test
    void passwordResetMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendPasswordResetVerificationCode("password@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[BidWorks AI] 비밀번호 재설정 인증번호 안내",
                "BidWorks AI 비밀번호 재설정 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                5
        );
    }

    @Test
    void loginMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendLoginVerificationCode("login@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[BidWorks AI] 로그인 인증번호 안내",
                "BidWorks AI 로그인 인증 요청입니다. 아래 인증번호를 입력해 주세요.",
                3
        );
    }

    @Test
    void sesIdentityFailureIsNotRetriedAndKeepsGenericApiResponse() {
        doThrow(new MailSendException(
                "Message rejected: Email address is not verified. "
                        + "The following identities failed the check: no-reply@example.com"
        )).when(javaMailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThatThrownBy(() ->
                mailService.sendSignupVerificationCode("signup@example.com", VERIFICATION_CODE)
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exception.getCode()).isEqualTo("MAIL_DELIVERY_FAILED");
            assertThat(exception.getMessage()).isEqualTo("mail delivery failed");
        });

        verify(javaMailSender, times(1)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void missingCredentialIsNamedDuringStartupValidation() {
        ReflectionTestUtils.setField(mailService, "mailUsername", "");

        assertThatThrownBy(mailService::validateStartupConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_USERNAME")
                .satisfies(exception ->
                        assertThat(exception.getMessage()).doesNotContain("test-password"));
    }

    private MimeMessage captureSentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private void assertVerificationMail(
            MimeMessage message,
            String expectedSubject,
            String expectedDescription,
            int expectedExpirationMinutes
    ) throws Exception {
        assertThat(message.getSubject()).isEqualTo(expectedSubject);
        assertThat(message.getSubject()).doesNotContain("[BidWorks AI] [BidWorks AI]");
        assertThat(message.getHeader("Auto-Submitted", null)).isNull();
        assertThat(message.getHeader("X-Auto-Response-Suppress", null)).isNull();
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("BidWorks AI");
        assertThat(message.getContentType()).startsWith("multipart/alternative");

        Object content = message.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
        Multipart alternative = (Multipart) content;
        assertThat(alternative.getCount()).isEqualTo(2);
        assertThat(alternative.getBodyPart(0).getContentType()).startsWith("text/plain");
        assertThat(alternative.getBodyPart(1).getContentType()).startsWith("text/html");
        assertThat(alternative.getBodyPart(0).getContent()).isInstanceOf(String.class);
        assertThat(alternative.getBodyPart(1).getContent()).isInstanceOf(String.class);

        String plainText = (String) alternative.getBodyPart(0).getContent();
        String htmlText = (String) alternative.getBodyPart(1).getContent();

        assertThat(plainText)
                .contains(expectedSubject)
                .contains(expectedDescription)
                .contains("인증번호: " + VERIFICATION_CODE)
                .contains("인증번호는 " + expectedExpirationMinutes + "분 동안 유효합니다.")
                .contains("본인이 요청하지 않았다면 이 메일을 무시해 주세요.")
                .contains("본 메일은 회신할 수 없는 자동 발송 메일입니다.")
                .contains("BidWorks AI 운영팀")
                .doesNotContain("PM Agent")
                .doesNotContain("홍길동")
                .doesNotContain("aivleschool1@gmail.com")
                .doesNotContain("test-password");

        assertThat(htmlText)
                .contains("<!doctype html>")
                .contains("<html lang=\"ko\">")
                .contains("role=\"presentation\"")
                .contains(expectedDescription)
                .contains(VERIFICATION_CODE)
                .contains("<strong>" + expectedExpirationMinutes + "분</strong>")
                .contains("본인이 요청하지 않았다면 이 메일을 무시해 주세요.")
                .contains("본 메일은 회신할 수 없는 자동 발송 메일입니다.")
                .contains("BidWorks AI 운영팀")
                .doesNotContain("PM Agent")
                .doesNotContain("홍길동")
                .doesNotContain("aivleschool1@gmail.com")
                .doesNotContain("test-password")
                .doesNotContain("쿠팡")
                .doesNotContain("gmail_quote")
                .doesNotContain("gmail_extra")
                .doesNotContain("<blockquote");

        assertThat(countOccurrences(htmlText, "BidWorks AI 운영팀")).isOne();
        assertThat(countOccurrences(htmlText, "본 메일은 회신할 수 없는 자동 발송 메일입니다.")).isOne();
    }

    private int countOccurrences(String text, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }
}
