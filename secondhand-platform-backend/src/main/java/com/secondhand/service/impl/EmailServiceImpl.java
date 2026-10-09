package com.secondhand.service.impl;

import com.secondhand.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Override
    public void sendPasswordResetCode(String email, String code) {
        sendCode(email, code, "校园闲物密码找回验证码", "你的密码找回验证码是：");
    }

    @Override
    public void sendEmailBindCode(String email, String code) {
        sendCode(email, code, "校园闲物邮箱绑定验证码", "你的邮箱绑定验证码是：");
    }

    @Override
    public void sendEmailUnbindCode(String email, String code) {
        sendCode(email, code, "校园闲物邮箱解绑验证码", "你的邮箱解绑验证码是：");
    }

    private void sendCode(String email, String code, String subject, String prefix) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(prefix + code + "。验证码 10 分钟内有效，请勿转发给他人。");
        mailSender.send(message);
    }
}
