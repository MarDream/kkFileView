package cn.keking.utils;

import cn.keking.config.ConfigConstants;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES加密解密工具类（目前AES比DES和DES3更安全，速度更快，对称加密一般采用AES）
 * 使用CBC模式，IV随机生成并前置到密文中
 */
public class AESUtil {
    private static final String aesKey = ConfigConstants.getaesKey();
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16;

    /**
     * AES解密（CBC模式）
     */
    public static String AesDecrypt(String url) {
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");

            byte[] encryptedWithIv = Base64.getDecoder().decode(url);

            // 提取IV（前16字节）和密文
            byte[] iv = new byte[IV_SIZE];
            byte[] encrypted = new byte[encryptedWithIv.length - IV_SIZE];
            System.arraycopy(encryptedWithIv, 0, iv, 0, IV_SIZE);
            System.arraycopy(encryptedWithIv, IV_SIZE, encrypted, 0, encrypted.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);

            byte[] original = cipher.doFinal(encrypted);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Given final block not properly padded. Such issues can arise if a bad key is used during decryption")) {
                return "Keyerror";
            } else if (e.getMessage() != null && e.getMessage().contains("Input byte array has incorrect ending byte")) {
                return "byteerror";
            } else if (e.getMessage() != null && e.getMessage().contains("Illegal base64 character")) {
                return "base64error";
            } else if (e.getMessage() != null && e.getMessage().contains("Input length must be multiple of 16 when decrypting with padded cipher")) {
                return "byteerror";
            } else {
                System.out.println("ace错误:" + e);
                return null;
            }
        }
    }

    /**
     * AES加密（CBC模式）
     */
    public static String aesEncrypt(String url) {
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");

            // 生成随机IV
            byte[] iv = new byte[IV_SIZE];
            java.security.SecureRandom random = new java.security.SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));

            // 将IV前置到密文前面
            byte[] encryptedWithIv = new byte[IV_SIZE + encrypted.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, IV_SIZE);
            System.arraycopy(encrypted, 0, encryptedWithIv, IV_SIZE, encrypted.length);

            return new String(Base64.getEncoder().encode(encryptedWithIv));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean aesKey(String aesKey) {
        if (aesKey == null) {
            System.out.print("Key为空null");
            return false;
        }
        // 判断Key是否为16位
        if (aesKey.length() != 16) {
            System.out.print("Key长度不是16位");
            return false;
        }
        return true;
    }
}
