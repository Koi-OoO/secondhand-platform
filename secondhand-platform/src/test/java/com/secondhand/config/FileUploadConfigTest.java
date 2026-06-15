package com.secondhand.config;

import com.secondhand.util.FileUploadUtil;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadConfigTest {

    @Test
    void fallsBackToLocalUploadWhenOssSecretIsBlank() {
        FileUploadConfig config = new FileUploadConfig();
        ReflectionTestUtils.setField(config, "endpoint", "oss-cn-beijing.aliyuncs.com");
        ReflectionTestUtils.setField(config, "accessKeyId", "access-key-id");
        ReflectionTestUtils.setField(config, "accessKeySecret", "   ");
        ReflectionTestUtils.setField(config, "bucketName", "bucket-name");
        ReflectionTestUtils.setField(config, "localStoragePath", "uploads");

        FileUploadUtil util = config.fileUploadUtil();

        assertThat(ReflectionTestUtils.getField(util, "endpoint")).isNull();
        assertThat(ReflectionTestUtils.getField(util, "accessKeyId")).isNull();
        assertThat(ReflectionTestUtils.getField(util, "accessKeySecret")).isNull();
        assertThat(ReflectionTestUtils.getField(util, "bucketName")).isNull();
        assertThat(Paths.get(ReflectionTestUtils.getField(util, "localStoragePath").toString())).isAbsolute();
    }
}
