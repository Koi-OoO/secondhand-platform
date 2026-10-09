package com.secondhand.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSecurityInfo {
    private String username;
    private String email;
    private Integer emailVerified;
    private boolean passwordSet;
}
