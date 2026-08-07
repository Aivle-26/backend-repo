package com.aivle26.aipm.Service.user;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
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
        ReflectionTestUtils.setField(mailService, "fromName", "PM Agent");
        ReflectionTestUtils.setField(mailService, "replyTo", "");
        ReflectionTestUtils.setField(mailService, "subjectPrefix", "[PM Agent]");
        ReflectionTestUtils.setField(mailService, "maxAttempts", 1);
        ReflectionTestUtils.setField(mailService, "retryDelayMs", 0L);
        ReflectionTestUtils.setField(
                mailService,
                "backgroundImageUrl",
                "https://assets.example.com/mail/verification-background.png"
        );
        given(javaMailSender.createMimeMessage())
                .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void signupMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendSignupVerificationCode("signup@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[PM Agent] 회원가입 이메일 인증",
                "회원가입을 완료하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 회원가입 화면에 입력하시면 인증이 완료됩니다.",
                5
        );
    }

    @Test
    void passwordResetMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendPasswordResetVerificationCode("password@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[PM Agent] 비밀번호 재설정 인증",
                "비밀번호 재설정을 완료하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.",
                5
        );
    }

    @Test
    void loginMailUsesSharedTemplateAndAlternativeMimeBody() throws Exception {
        mailService.sendLoginVerificationCode("login@example.com", VERIFICATION_CODE);

        assertVerificationMail(
                captureSentMessage(),
                "[PM Agent] 로그인 인증",
                "로그인을 계속하려면 아래 인증번호를 입력해 주세요.",
                "아래 인증번호를 로그인 화면에 입력하시면 인증이 완료됩니다.",
                3
        );
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
            String expectedHeadline,
            String expectedInstruction,
            int expectedExpirationMinutes
    ) throws Exception {
        assertThat(message.getSubject()).isEqualTo(expectedSubject);
        assertThat(message.getSubject()).doesNotContain("[PM Agent] [PM Agent]");
        assertThat(message.getHeader("Auto-Submitted", null)).isEqualTo("auto-generated");
        assertThat(message.getHeader("X-Auto-Response-Suppress", null)).isEqualTo("All");
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
                .contains(expectedHeadline)
                .contains(expectedInstruction)
                .contains("인증번호: " + VERIFICATION_CODE)
                .contains("인증번호는 " + expectedExpirationMinutes + "분 동안 유효합니다.")
                .contains("PM Agent")
                .contains("대표: TEAM26")
                .contains("주소: 부산광역시 동구 초량중로 29, 3층")
                .contains("이메일: aivleschool1@gmail.com")
                .doesNotContain("대표: 홍길동")
                .doesNotContain("test-password");

        assertThat(htmlText)
                .contains("<!doctype html>")
                .contains("<html lang=\"ko\">")
                .contains("role=\"presentation\"")
                .contains(expectedHeadline)
                .contains(expectedInstruction)
                .contains(VERIFICATION_CODE)
                .contains(">" + expectedExpirationMinutes + "분</strong>")
                .contains("PM Agent")
                .contains("대표: TEAM26")
                .contains("주소: 부산광역시 동구 초량중로 29, 3층")
                .contains("mailto:aivleschool1@gmail.com")
                .contains("aivleschool1@gmail.com")
                .contains("class=\"verification-code\"")
                .contains("&#9719;")
                .contains("&#10003;")
                .contains("background-image:url('https://assets.example.com/mail/verification-background.png')")
                .doesNotContain("<img")
                .doesNotContain("background=\"")
                .doesNotContain("cid:")
                .doesNotContain(">AI</td>")
                .doesNotContain("대표: 홍길동")
                .doesNotContain("test-password")
                .doesNotContain("쿠팡")
                .doesNotContain("gmail_quote")
                .doesNotContain("gmail_extra")
                .doesNotContain("<blockquote");

        assertThat(countOccurrences(htmlText, "대표: TEAM26")).isOne();
        assertThat(countOccurrences(htmlText, "부산광역시 동구 초량중로 29, 3층")).isOne();
        assertThat(countOccurrences(htmlText, "mailto:aivleschool1@gmail.com")).isOne();
        assertThat(countOccurrences(htmlText, "본 메일은 발신 전용으로 회신되지 않습니다.")).isOne();
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
