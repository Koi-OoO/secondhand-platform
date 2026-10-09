package com.secondhand.service;

import com.secondhand.entity.ChangePasswordRequest;
import com.secondhand.entity.EmailBindRequest;
import com.secondhand.entity.EmailBindVerifyRequest;
import com.secondhand.entity.EmailUnbindRequest;
import com.secondhand.util.Result;

public interface AccountSecurityService {
    Result getSecurityInfo(Long userId);

    Result requestEmailBindCode(Long userId, EmailBindRequest request);

    Result verifyEmailBindCode(Long userId, EmailBindVerifyRequest request);

    Result requestEmailUnbindCode(Long userId);

    Result unbindEmail(Long userId, EmailUnbindRequest request);

    Result changePassword(Long userId, ChangePasswordRequest request);
}
