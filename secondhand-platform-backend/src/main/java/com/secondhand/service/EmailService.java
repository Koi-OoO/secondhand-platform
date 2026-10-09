package com.secondhand.service;

public interface EmailService {
    void sendPasswordResetCode(String email, String code);

    void sendEmailBindCode(String email, String code);

    void sendEmailUnbindCode(String email, String code);
}
