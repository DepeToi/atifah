package com.atifah.common.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 配置项加解密（AES-256-GCM，纯 JDK 实现，不引入第三方依赖）。
 *
 * <p>配置文件中的敏感值写成 {@code ENC(base64(iv||ciphertext+tag))}，由
 * {@link ConfigCryptoEnvironmentPostProcessor} 在启动时解密；主密钥只允许通过环境变量
 * {@value #KEY_ENV} 注入（本地调试可用同名 JVM system property），禁止写进仓库。
 *
 * <p>设计约束：
 * <ul>
 *   <li>每次加密使用 12 字节随机 IV，同一明文多次加密结果不同，防止比对；</li>
 *   <li>密钥由主密钥口令经 SHA-256 派生为 32 字节，口令长度不限；</li>
 *   <li>异常信息不包含密文与密钥，避免日志二次泄露。</li>
 * </ul>
 */
public final class ConfigCipher {

    /** 密文包装前缀，完整形式 {@code ENC(...)}。 */
    public static final String PREFIX = "ENC(";

    /** 主密钥环境变量名；也可用同名 JVM system property（仅本地调试）。 */
    public static final String KEY_ENV = "CONFIG_ENC_KEY";

    private static final String SUFFIX = ")";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConfigCipher() {
    }

    /**
     * 判断值是否为 {@code ENC(...)} 密文。
     */
    public static boolean isEncrypted(String value) {
        return value != null
                && value.length() > PREFIX.length() + SUFFIX.length()
                && value.startsWith(PREFIX)
                && value.endsWith(SUFFIX);
    }

    /**
     * 加密明文，返回可直接写入 yml 的 {@code ENC(...)} 值。
     */
    public static String encrypt(String plainText, String masterKey) {
        if (plainText == null) {
            throw new IllegalArgumentException("待加密明文不能为空");
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, toSecretKey(masterKey), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, payload, IV_LENGTH, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload) + SUFFIX;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("配置项加密失败：当前 JDK 不支持 AES-256-GCM", e);
        }
    }

    /**
     * 解密 {@code ENC(...)} 值；密钥不匹配、密文被截断或篡改时抛异常，不返回密文兜底。
     */
    public static String decrypt(String encryptedValue, String masterKey) {
        if (!isEncrypted(encryptedValue)) {
            throw new IllegalArgumentException("不是 ENC(...) 密文，无法解密");
        }
        byte[] payload = Base64.getDecoder()
                .decode(encryptedValue.substring(PREFIX.length(), encryptedValue.length() - SUFFIX.length()));
        if (payload.length <= IV_LENGTH) {
            throw new IllegalStateException("配置项密文格式非法：长度不足，密文可能被截断");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, toSecretKey(masterKey),
                    new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH));
            byte[] plainText = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 不拼接密文与密钥：异常会进日志，避免二次泄露
            throw new IllegalStateException("配置项解密失败：请确认 " + KEY_ENV + " 与加密时一致，且密文未被改动", e);
        }
    }

    /**
     * 读取主密钥：优先环境变量，其次同名 system property；未配置返回 {@code null}。
     */
    public static String resolveMasterKey() {
        String key = System.getenv(KEY_ENV);
        if (key == null || key.isBlank()) {
            key = System.getProperty(KEY_ENV);
        }
        return (key == null || key.isBlank()) ? null : key;
    }

    /**
     * 生成 32 字节随机主密钥（base64url 无填充），用于 {@value #KEY_ENV}。
     */
    public static String generateMasterKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    private static SecretKeySpec toSecretKey(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("缺少配置项主密钥 " + KEY_ENV);
        }
        try {
            byte[] digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用，无法派生配置项主密钥", e);
        }
    }
}
