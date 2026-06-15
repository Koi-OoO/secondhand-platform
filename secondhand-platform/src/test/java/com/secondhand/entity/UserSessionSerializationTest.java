package com.secondhand.entity;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;

class UserSessionSerializationTest {

    @Test
    void userCanBeSerializedForRedisBackedSession() {
        User user = new User();
        user.setId(1L);
        user.setUsername("session_user");
        user.setNickname("Session User");

        assertThatCode(() -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(user);
            }
        }).doesNotThrowAnyException();
    }
}
