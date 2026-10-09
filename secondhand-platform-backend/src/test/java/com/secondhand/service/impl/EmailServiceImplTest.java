package com.secondhand.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.secondhand.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailServiceImplTest {

    @Test
    void sendsMailFromConfiguredSmtpUsername() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailServiceImpl service = new EmailServiceImpl();
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "senderEmail", "sender@example.com");

        service.sendEmailBindCode("receiver@example.com", "123456");

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getValue().getTo()).containsExactly("receiver@example.com");
    }
}
