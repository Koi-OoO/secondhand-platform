package com.secondhand.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadPathResolverTest {

    @Test
    void resolvesRelativePathAgainstProvidedBaseDirectory() {
        Path baseDir = Paths.get("D:/workspace/app");

        Path resolved = UploadPathResolver.resolve("uploads", baseDir);

        assertThat(resolved).isEqualTo(baseDir.resolve("uploads").normalize());
    }

    @Test
    void keepsAbsolutePathUnchanged() {
        Path absolute = Paths.get("D:/data/uploads").toAbsolutePath().normalize();

        Path resolved = UploadPathResolver.resolve(absolute.toString(), Paths.get("D:/workspace/app"));

        assertThat(resolved).isEqualTo(absolute);
    }
}
