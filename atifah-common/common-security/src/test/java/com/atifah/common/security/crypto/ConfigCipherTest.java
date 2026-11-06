package com.atifah.common.security.crypto;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 配置项加解密单元测试。
 */
class ConfigCipherTest {

    private static final String MASTER_KEY = "unit-test-master-key";

    @Test
    void encryptAndDecryptRoundTrip() {
        String cipherText = ConfigCipher.encrypt("MySql@2026", MASTER_KEY);

        assertThat(ConfigCipher.isEncrypted(cipherText)).isTrue();
        assertThat(ConfigCipher.decrypt(cipherText, MASTER_KEY)).isEqualTo("MySql@2026");
    }

    @Test
    void samePlainTextProducesDifferentCipherText() {
        String first = ConfigCipher.encrypt("same", MASTER_KEY);
        String second = ConfigCipher.encrypt("same", MASTER_KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(ConfigCipher.decrypt(second, MASTER_KEY)).isEqualTo("same");
    }

    @Test
    void decryptWithWrongKeyFailsWithoutLeakingCipherText() {
        String cipherText = ConfigCipher.encrypt("secret-value", MASTER_KEY);

        Throwable thrown = catchThrowable(() -> ConfigCipher.decrypt(cipherText, "another-key"));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .contains(ConfigCipher.KEY_ENV)
                .doesNotContain(cipherText)
                .doesNotContain("secret-value");
    }

    @Test
    void tamperedCipherTextFails() {
        String cipherText = ConfigCipher.encrypt("secret-value", MASTER_KEY);
        String body = cipherText.substring(ConfigCipher.PREFIX.length(), cipherText.length() - 1);
        byte[] payload = Base64.getDecoder().decode(body);
        payload[payload.length - 1] ^= 0xFF;
        String tampered = ConfigCipher.PREFIX + Base64.getEncoder().encodeToString(payload) + ")";

        assertThatThrownBy(() -> ConfigCipher.decrypt(tampered, MASTER_KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isEncryptedRejectsPlainTextAndIncompleteWrapper() {
        assertThat(ConfigCipher.isEncrypted(null)).isFalse();
        assertThat(ConfigCipher.isEncrypted("root")).isFalse();
        assertThat(ConfigCipher.isEncrypted("ENC()")).isFalse();
        assertThat(ConfigCipher.isEncrypted("ENC(abc")).isFalse();
        assertThat(ConfigCipher.isEncrypted("ENC(abc)")).isTrue();
    }

    @Test
    void generatedMasterKeyIs32Bytes() {
        String masterKey = ConfigCipher.generateMasterKey();

        assertThat(Base64.getUrlDecoder().decode(masterKey)).hasSize(32);
        assertThat(ConfigCipher.encrypt("x", masterKey)).isNotEqualTo(ConfigCipher.encrypt("x", masterKey));
    }
}
