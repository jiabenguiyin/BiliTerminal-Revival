package com.RobinNotBad.BiliClient.util;

import android.util.Base64;

import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/** Bilibili web login RSA password encryption. */
public final class PasswordEncryptUtil {
    private PasswordEncryptUtil() {
    }

    public static String encryptPassword(String password, String hash, String publicKeyPem)
            throws Exception {
        String key = publicKeyPem == null ? "" : publicKeyPem.trim()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim();
        byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] plain = ((hash == null ? "" : hash) + (password == null ? "" : password))
                .getBytes(Charset.forName("UTF-8"));
        return Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP);
    }
}
