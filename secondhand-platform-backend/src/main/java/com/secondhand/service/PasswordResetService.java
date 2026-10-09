package com.secondhand.service;

import com.secondhand.entity.PasswordResetConfirmRequest;
import com.secondhand.entity.PasswordResetRequest;
import com.secondhand.entity.PasswordResetVerifyRequest;
import com.secondhand.util.Result;

public interface PasswordResetService {
    Result requestCode(PasswordResetRequest request);

    Result verifyCode(PasswordResetVerifyRequest request);

    Result resetPassword(PasswordResetConfirmRequest request);
}
