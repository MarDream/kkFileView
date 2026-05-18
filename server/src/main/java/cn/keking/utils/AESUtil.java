package cn.keking.utils;

import cn.keking.config.ConfigConstants;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES加密解密工具类（目前AES比DES和DES3更安全，速度更快，对称加密一般采用AES）
 * 默认使用CBC模式，IV随机生成并前置到密文中，同时兼容历史ECB密文解密
 */
public class AESUtil {
    private static final String CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String LEGACY_ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int IV_SIZE = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * AES解密（CBC模式）
     */
    public static String AesDecrypt(String url) {
        String aesKey = ConfigConstants.getaesKey();
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(url);
            try {
                return decryptCbc(encryptedWithIv, aesKey);
            } catch (GeneralSecurityException | IllegalArgumentException cbcException) {
                return decryptLegacyEcb(encryptedWithIv, aesKey, cbcException);
            }
        } catch (IllegalArgumentException e) {
            return "base64error";
        } catch (Exception e) {
            return mapDecryptError(e);
        }
    }

    /**
     * AES加密（CBC模式）
     */
    public static String aesEncrypt(String url) {
        String aesKey = ConfigConstants.getaesKey();
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");

            byte[] iv = new byte[IV_SIZE];
            SECURE_RANDOM.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(CBC_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));

            byte[] encryptedWithIv = new byte[IV_SIZE + encrypted.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, IV_SIZE);
            System.arraycopy(encrypted, 0, encryptedWithIv, IV_SIZE, encrypted.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decryptCbc(byte[] encryptedWithIv, String aesKey) throws GeneralSecurityException {
        if (encryptedWithIv.length <= IV_SIZE) {
            throw new GeneralSecurityException("CBC payload is too short");
        }

        byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        byte[] iv = new byte[IV_SIZE];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_SIZE];

        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_SIZE);
        System.arraycopy(encryptedWithIv, IV_SIZE, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(CBC_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(iv));
        byte[] original = cipher.doFinal(encrypted);
        return new String(original, StandardCharsets.UTF_8);
    }

    private static String decryptLegacyEcb(byte[] encrypted, String aesKey, Exception cbcException) {
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance(LEGACY_ECB_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] original = cipher.doFinal(encrypted);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception legacyException) {
            return mapDecryptError(legacyException.getMessage() != null ? legacyException : cbcException);
        }
    }

    private static String mapDecryptError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return null;
        }
        if (message.contains("Given final block not properly padded")
                || message.contains("pad block corrupted")
                || message.contains("bad key")) {
            return "Keyerror";
        }
        if (message.contains("Input byte array has incorrect ending byte")
                || message.contains("Input length must be multiple of 16")
                || message.contains("Wrong IV length")) {
            return "byteerror";
        }
        if (message.contains("Illegal base64 character")) {
            return "base64error";
        }
        return null;
    }

    public static boolean aesKey(String aesKey) {
        if (aesKey == null || "false".equalsIgnoreCase(aesKey)) {
            return false;
        }
        if (aesKey.length() != 16) {
            return false;
        }
        return true;
    }
}
