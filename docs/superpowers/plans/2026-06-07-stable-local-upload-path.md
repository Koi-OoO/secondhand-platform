# Stable Local Upload Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the current "OSS first, otherwise local upload" behavior while making local uploaded images resolve from a stable filesystem path independent of the backend process working directory.

**Architecture:** Introduce one shared local-upload path resolver that turns configured relative paths into a stable absolute directory. Use that resolver both when saving uploaded files and when exposing `/uploads/**` static resources so write-path and read-path always match.

**Tech Stack:** Spring Boot, Spring MVC, JUnit 5, AssertJ

---

### Task 1: Add failing tests for path resolution

**Files:**
- Create: `secondhand-platform/src/test/java/com/secondhand/util/UploadPathResolverTest.java`
- Modify: `secondhand-platform/src/test/java/com/secondhand/config/FileUploadConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=UploadPathResolverTest,FileUploadConfigTest test`
Expected: FAIL because `UploadPathResolver` does not exist and `FileUploadConfig` still returns the raw relative local path.

- [ ] **Step 3: Extend config test with expected absolute path**

```java
@Test
void resolvesLocalUploadPathBeforeCreatingUtility() {
    FileUploadConfig config = new FileUploadConfig();
    ReflectionTestUtils.setField(config, "localStoragePath", "uploads");

    FileUploadUtil util = config.fileUploadUtil();

    String configured = (String) ReflectionTestUtils.getField(util, "localStoragePath");
    assertThat(Paths.get(configured)).isAbsolute();
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=UploadPathResolverTest,FileUploadConfigTest test`
Expected: FAIL with assertion showing `localStoragePath` is still relative.

### Task 2: Implement shared path resolution

**Files:**
- Create: `secondhand-platform/src/main/java/com/secondhand/util/UploadPathResolver.java`
- Modify: `secondhand-platform/src/main/java/com/secondhand/config/FileUploadConfig.java`
- Modify: `secondhand-platform/src/main/java/com/secondhand/config/WebMvcConfig.java`

- [ ] **Step 1: Write minimal resolver implementation**

```java
public final class UploadPathResolver {

    private UploadPathResolver() {
    }

    public static Path resolve(String configuredPath) {
        return resolve(configuredPath, detectBaseDir());
    }

    static Path resolve(String configuredPath, Path baseDir) {
        Path path = Paths.get(configuredPath).normalize();
        if (path.isAbsolute()) {
            return path;
        }
        return baseDir.resolve(path).normalize();
    }
}
```

- [ ] **Step 2: Use resolver in `FileUploadConfig`**

```java
String resolvedLocalPath = UploadPathResolver.resolve(localStoragePath).toString();
if (hasText(endpoint) && hasText(accessKeyId) && hasText(accessKeySecret) && hasText(bucketName)) {
    return new FileUploadUtil(endpoint, accessKeyId, accessKeySecret, bucketName, resolvedLocalPath);
}
return new FileUploadUtil(resolvedLocalPath);
```

- [ ] **Step 3: Use resolver in `WebMvcConfig`**

```java
@Value("${app.upload.local-path:uploads}")
private String localStoragePath;

registry.addResourceHandler("/uploads/**")
        .addResourceLocations("file:" + ensureTrailingSlash(UploadPathResolver.resolve(localStoragePath)))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -Dtest=UploadPathResolverTest,FileUploadConfigTest test`
Expected: PASS

### Task 3: Verify no regression in existing upload behavior

**Files:**
- Modify: `secondhand-platform/src/test/java/com/secondhand/config/FileUploadConfigTest.java`

- [ ] **Step 1: Keep OSS fallback behavior test green**

```java
assertThat(ReflectionTestUtils.getField(util, "endpoint")).isNull();
assertThat(ReflectionTestUtils.getField(util, "bucketName")).isNull();
```

- [ ] **Step 2: Run focused regression checks**

Run: `mvn -Dtest=FileUploadConfigTest test`
Expected: PASS with both behaviors verified: OSS missing still falls back to local, and local path is absolute.

- [ ] **Step 3: Run broader config verification**

Run: `mvn -Dtest=FileUploadConfigTest,OpenApiConfigTest test`
Expected: PASS
