package cn.minims.desk.framework.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 集成配置加密（AES-256-GCM）。
 *
 * <p>主密钥优先级：环境变量 MINIDESK_SECRET → data/.secret（首次自动生成）。
 * 密文格式：enc:base64(iv + cipherText)，未带前缀的值视为明文（兼容手工写库）。
 */
@Component
public class AesGcmCipher {

    private static final Logger log = LoggerFactory.getLogger(AesGcmCipher.class);
    private static final String PREFIX = "enc:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(@Value("${minidesk.secret:}") String secret,
                        @Value("${minidesk.data-dir:./data}") String dataDir) {
        this.key = new SecretKeySpec(deriveKey(secret, dataDir), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("encrypt failed", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("decrypt failed, treat as plain text");
            return null;
        }
    }

    /** 只回显后四位，前端永不拿到明文 */
    public String mask(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        if (plain.length() <= 4) {
            return "****";
        }
        return "****" + plain.substring(plain.length() - 4);
    }

    private byte[] deriveKey(String secret, String dataDir) {
        String material = secret;
        if (material == null || material.isBlank()) {
            material = loadOrCreateLocalSecret(dataDir);
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("derive key failed", ex);
        }
    }

    private String loadOrCreateLocalSecret(String dataDir) {
        try {
            Path dir = Path.of(dataDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(".secret");
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String generated = Base64.getEncoder().encodeToString(bytes);
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows 不支持 POSIX 权限，跳过
            }
            log.info("generated local secret at {}", file.toAbsolutePath());
            return generated;
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot init local secret", ex);
        }
    }
}
