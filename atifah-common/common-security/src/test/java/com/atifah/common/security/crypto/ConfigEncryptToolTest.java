package com.atifah.common.security.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置密文离线工具测试：明文只从 stdin 读取，禁止通过命令行参数传入。
 */
class ConfigEncryptToolTest {

    private static final String MASTER_KEY = ConfigCipher.generateMasterKey();

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private int run(String[] args, String stdin) {
        return ConfigEncryptTool.run(
                args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output),
                null,
                () -> MASTER_KEY);
    }

    @Test
    void encryptReadsPlainTextFromStdin() {
        int exitCode = run(new String[]{"encrypt"}, "secret\n");

        assertThat(exitCode).isZero();
        String cipherText = output.toString(StandardCharsets.UTF_8).trim();
        assertThat(ConfigCipher.isEncrypted(cipherText)).isTrue();
        assertThat(ConfigCipher.decrypt(cipherText, MASTER_KEY)).isEqualTo("secret");
    }

    @Test
    void encryptRejectsPlainTextArgument() {
        int exitCode = run(new String[]{"encrypt", "secret"}, "");

        assertThat(exitCode).isNotZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("命令行");
    }

    @Test
    void decryptRoundTrip() {
        String cipherText = ConfigCipher.encrypt("secret", MASTER_KEY);

        int exitCode = run(new String[]{"decrypt", cipherText}, "");

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8).trim()).isEqualTo("secret");
    }

    @Test
    void keygenOutputsValidMasterKey() {
        int exitCode = run(new String[]{"keygen"}, "");

        assertThat(exitCode).isZero();
        assertThat(Base64.getUrlDecoder().decode(output.toString(StandardCharsets.UTF_8).trim())).hasSize(32);
    }

    @Test
    void encryptFailsWithoutMasterKey() {
        int exitCode = ConfigEncryptTool.run(
                new String[]{"encrypt"},
                new ByteArrayInputStream("secret\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output),
                null,
                () -> null);

        assertThat(exitCode).isNotZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains(ConfigCipher.KEY_ENV);
    }
}
