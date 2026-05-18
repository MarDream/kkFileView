package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AESUtilTests {

    private static final String AES_KEY = "1234567890123456";

    @AfterEach
    void resetKey() {
        ConfigConstants.setaesKeyValue(ConfigConstants.DEFAULT_AES_KEY);
    }

    @Test
    void shouldEncryptAndDecryptWithCbcMode() {
        ConfigConstants.setaesKeyValue(AES_KEY);
        String plainText = "https://file.kkview.cn/demo/sample.pdf";

        String encrypted = AESUtil.aesEncrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, AESUtil.AesDecrypt(encrypted));
    }

    @Test
    void shouldDecryptLegacyEcbPayload() throws Exception {
        ConfigConstants.setaesKeyValue(AES_KEY);
        String plainText = "https://file.kkview.cn/demo/legacy.docx";

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        String legacyPayload = Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));

        assertEquals(plainText, AESUtil.AesDecrypt(legacyPayload));
    }
}
