package com.fairyshalltear.freshfruit.ai.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;

@Component
public class AiCredentials {
    private final byte[] key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();
    public AiCredentials(@Value("${freshfruit.ai.encryption-key:}") String value) {
        byte[] decoded = null;
        try { decoded = Base64.getDecoder().decode(value); } catch (IllegalArgumentException ignored) { }
        key = decoded != null && decoded.length == 32 ? decoded : null;
        try { keyId = key == null ? "unavailable" : Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key)).substring(0,12); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public boolean available() { return key != null; }
    public String encrypt(String secret) {
        if (secret == null || secret.isBlank()) return null;
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,nonce));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            return "v1:" + keyId + ":" + encode(nonce) + ":" + encode(cipher.doFinal(secret.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw unavailable("AI_CREDENTIAL_ERROR", "模型凭据无法加密，请检查部署主密钥"); }
    }
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        requireKey();
        try {
            String[] pieces = ciphertext.split(":");
            if (pieces.length != 4 || !pieces[0].equals("v1") || !pieces[1].equals(keyId)) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,Base64.getDecoder().decode(pieces[2])));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(pieces[3])),StandardCharsets.UTF_8);
        } catch (Exception error) { throw unavailable("AI_CREDENTIAL_ERROR", "模型凭据不可读取，请核对主密钥或重新配置凭据"); }
    }
    private String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private void requireKey() { if (!available()) throw unavailable("AI_ENCRYPTION_KEY_REQUIRED", "请先配置32字节Base64格式的AI_CONFIG_ENCRYPTION_KEY"); }
}
