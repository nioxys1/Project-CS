import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.Key;
import java.util.Base64;

public class AESUtil {
    private static final String ALGORITHM = "AES";
    // 16 characters for 128-bit encryption
    private static final String SECRET = "MySuperSecretKey"; // In production, use environment variables
    
    public static CipherInputStream getDecryptedInputStream(InputStream is) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, getKey());
        return new CipherInputStream(is, cipher);
    }

    public static CipherOutputStream getEncryptedOutputStream(OutputStream os) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        return new CipherOutputStream(os, cipher);
    }

    private static Key getKey() {
        // Ensure the key is exactly 16 bytes (128 bits)
        byte[] keyBytes = new byte[16];
        byte[] secretBytes = SECRET.getBytes();
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, keyBytes.length));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // Additional utility methods for string encryption/decryption
    public static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, getKey());
        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(original);
    }
}
