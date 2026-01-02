import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;

/**
 * Cryptographic utilities - SECURE LAYOUT (PII ENCRYPTED)
 * 
 * New Card Structure (80 bytes):
 * [0-1]   UserID (2 bytes)
 * [2-49]  Encrypted Area (48 bytes, AES-128/ECB/NoPadding):
 *          Plaintext layout (48 bytes):
 *            - Balance (4 bytes, BE)
 *            - ExpiryDays (2 bytes, BE)
 *            - DOB Day (1 byte)
 *            - DOB Month (1 byte)
 *            - DOB Year (2 bytes, BE)
 *            - NameLen (1 byte, 0..21)
 *            - FullName UTF-8 bytes (max 21)
 *            - CCCD ASCII (12 bytes)
 *            - Padding with zeros to 48 bytes (4 bytes)
 * [50]    PIN Retry Counter (1 byte)
 * [51-66] PIN Hash (16 bytes, SHA-256 truncated to 16)
 * [67-79] Reserved (zeros)
 */
public class CryptoHelper {
    
    /**
     * Derive AES-128 key from PIN using SHA-256 (truncate to 16 bytes)
     * PIN is hashed as 6-byte ASCII string (e.g., "123456")
     */
    public static SecretKeySpec deriveAESKeyFromPIN(String pin) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] pinBytes = pin.getBytes("ASCII");
        byte[] hash = sha256.digest(pinBytes); // 32 bytes
        
        byte[] aesKeyBytes = new byte[16];
        System.arraycopy(hash, 0, aesKeyBytes, 0, 16);
        
        SecretKeySpec key = new SecretKeySpec(aesKeyBytes, "AES");
        printHex("🔑 PC AES KEY (pin=" + pin + ")", aesKeyBytes);
        return key;
    }
    
    /**
     * Hash PIN with SHA-256 (truncated to 16 bytes to fit layout)
     * PIN is hashed as 6-byte ASCII string (e.g., "123456")
     */
    public static byte[] hashPIN(String pin) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] pinBytes = pin.getBytes("ASCII");
        byte[] fullHash = sha256.digest(pinBytes); // 32 bytes
        
        byte[] truncatedHash = new byte[16];
        System.arraycopy(fullHash, 0, truncatedHash, 0, 16);
        
        return truncatedHash;
    }
    
    /**
     * Debug print hex
     */
    public static void printHex(String label, byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (byte b : data) {
        sb.append(String.format("%02X ", b & 0xFF));
    }
    System.out.println(label + ": " + sb.toString());
}
    
    /**
     * Encrypt two-block sensitive payload (32 bytes) using AES-128 (ECB/NoPadding)
     */
    public static byte[] encryptSensitivePayload(byte[] payload48, String pin) throws Exception {
        if (payload48 == null || payload48.length != 48) {
            throw new IllegalArgumentException("Payload must be exactly 48 bytes");
        }
        SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        return cipher.doFinal(payload48);
    }
    
    public static byte[] decryptSensitivePayload(byte[] encrypted48, String pin) throws Exception {
        if (encrypted48 == null || encrypted48.length != 48) {
            throw new IllegalArgumentException("Encrypted data must be 48 bytes");
        }
        SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        return cipher.doFinal(encrypted48);
    }
    
    /**
     * Parse RSA public key
     */
    public static PublicKey parseRSAPublicKey(byte[] keyData) throws Exception {
        if (keyData.length < 131) {
            throw new IllegalArgumentException("Key data must be at least 131 bytes");
        }
        
        byte[] modulusBytes = new byte[128];
        System.arraycopy(keyData, 0, modulusBytes, 0, 128);
        BigInteger modulus = new BigInteger(1, modulusBytes);
        
        byte[] exponentBytes = new byte[3];
        System.arraycopy(keyData, 128, exponentBytes, 0, 3);
        BigInteger exponent = new BigInteger(1, exponentBytes);
        
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        return keyFactory.generatePublic(keySpec);
    }
    
    /**
     * Verify RSA signature
     */
    public static boolean verifySignature(byte[] challenge, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initVerify(publicKey);
        sig.update(challenge);
        return sig.verify(signature);
    }
    
    /**
     * Generate random challenge (32 bytes)
     */
    public static byte[] generateChallenge() {
        byte[] challenge = new byte[32];
        new java.security.SecureRandom().nextBytes(challenge);
        return challenge;
    }
    
    /**
     * Build card data for WRITE - 80-byte layout with 48-byte encrypted block
     */
    public static byte[] buildCardData(int userId, int balance, short expiry, 
                                       String pin, byte pinRetry,
                                       byte dobDay, byte dobMonth, short dobYear,
                                       String fullName,
                                       String cccd) throws Exception {
        byte[] cardData = new byte[80];
        
        // [0-1] UserID
        cardData[0] = (byte) ((userId >> 8) & 0xFF);
        cardData[1] = (byte) (userId & 0xFF);
        
        // Build 48-byte plaintext payload
        byte[] payload = new byte[48];
        // Balance
        payload[0] = (byte) ((balance >> 24) & 0xFF);
        payload[1] = (byte) ((balance >> 16) & 0xFF);
        payload[2] = (byte) ((balance >> 8) & 0xFF);
        payload[3] = (byte) (balance & 0xFF);
        // Expiry
        payload[4] = (byte) ((expiry >> 8) & 0xFF);
        payload[5] = (byte) (expiry & 0xFF);
        // DOB
        payload[6] = dobDay;
        payload[7] = dobMonth;
        payload[8] = (byte) ((dobYear >> 8) & 0xFF);
        payload[9] = (byte) (dobYear & 0xFF);
        // Name
        byte[] nameBytes = fullName != null ? fullName.getBytes("UTF-8") : new byte[0];
        int nameLen = Math.min(nameBytes.length, 21); // fit in 32 bytes total
        payload[10] = (byte) (nameLen & 0xFF);
        if (nameLen > 0) {
            System.arraycopy(nameBytes, 0, payload, 11, nameLen);
        }
        // CCCD (ASCII) 12 bytes at payload[32..43]
        byte[] cccdBytes = cccd != null ? cccd.getBytes("US-ASCII") : new byte[0];
        int copyLen = Math.min(cccdBytes.length, 12);
        if (copyLen > 0) {
            System.arraycopy(cccdBytes, 0, payload, 32, copyLen);
        }
        // Padding with zeros is automatic in new byte[]
        // Encrypt and place into [2-49]
        byte[] enc = encryptSensitivePayload(payload, pin);
        System.arraycopy(enc, 0, cardData, 2, 48);

        // [50] PIN Retry Counter
        cardData[50] = pinRetry;

        // [51-66] PIN Hash (16 bytes)
        byte[] pinHash = hashPIN(pin);
        System.arraycopy(pinHash, 0, cardData, 51, 16);

        // [67-79] reserved zeros
        
        return cardData;
    }
    
    /**
     * Parse ENCRYPTED card data (from READ command)
     * Requires PIN to decrypt balance/expiry
     * 
     * @param data 80-byte encrypted card data
     * @param pin PIN for decryption (6-digit string)
     */
    public static CardData parseEncryptedCardData(byte[] data, String pin) throws Exception {
        if (data.length < 80) {
            throw new IllegalArgumentException("Card data must be 80 bytes");
        }
        if (pin == null || pin.isEmpty()) {
            throw new IllegalArgumentException("PIN required to decrypt data");
        }
        
        System.out.println("=== DEBUG parseEncryptedCardData (48B ENC) ===");
        
        CardData card = new CardData();
        
        // [0-1] UserID
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // [2-49] Encrypted payload (48 bytes) - decrypt with PIN
        try {
            byte[] encryptedBlock = new byte[48];
            System.arraycopy(data, 2, encryptedBlock, 0, 48);
            byte[] decrypted = decryptSensitivePayload(encryptedBlock, pin);

            // Parse fields
            card.balance = ((decrypted[0] & 0xFF) << 24) |
                          ((decrypted[1] & 0xFF) << 16) |
                          ((decrypted[2] & 0xFF) << 8) |
                          (decrypted[3] & 0xFF);
            card.expiryDays = (short) (((decrypted[4] & 0xFF) << 8) | (decrypted[5] & 0xFF));
            card.dobDay = decrypted[6];
            card.dobMonth = decrypted[7];
            card.dobYear = (short) (((decrypted[8] & 0xFF) << 8) | (decrypted[9] & 0xFF));
            int nameLen = Math.max(0, Math.min(21, decrypted[10] & 0xFF));
            if (nameLen > 0) {
                card.fullName = new String(decrypted, 11, nameLen, "UTF-8").trim();
            } else {
                card.fullName = "";
            }
            // CCCD (ASCII) at [32..43]
            String cccdStr = new String(decrypted, 32, 12, "US-ASCII").trim();
            card.cccd = cccdStr;
        } catch (Exception e) {
            throw new Exception("Failed to decrypt card data: " + e.getMessage(), e);
        }
        
        // [50] PIN Retry
        card.pinRetry = data[50];
        
        return card;
    }
    
    /**
     * Parse DECRYPTED card data (from VERIFY_PIN response)
     * Data [2-17] is already plaintext, no decryption needed
     * 
     * @param data 80-byte decrypted card data
     * @param pin PIN value (for storing in CardData.pin field)
     */
    public static CardData parseDecryptedCardData(byte[] data, String pin) throws Exception {
        if (data.length < 80) {
            throw new IllegalArgumentException("Card data must be 80 bytes");
        }
        
        System.out.println("=== DEBUG parseDecryptedCardData (48B plain) ===");
        
        CardData card = new CardData();
        
        // [0-1] UserID
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // [2-49] plain payload (48 bytes)
        byte[] plain = new byte[48];
        System.arraycopy(data, 2, plain, 0, 48);
        card.balance = ((plain[0] & 0xFF) << 24) |
                      ((plain[1] & 0xFF) << 16) |
                      ((plain[2] & 0xFF) << 8) |
                      (plain[3] & 0xFF);
        card.expiryDays = (short) (((plain[4] & 0xFF) << 8) | (plain[5] & 0xFF));
        card.dobDay = plain[6];
        card.dobMonth = plain[7];
        card.dobYear = (short) (((plain[8] & 0xFF) << 8) | (plain[9] & 0xFF));
        int nameLen = Math.max(0, Math.min(21, plain[10] & 0xFF));
        card.fullName = nameLen > 0 ? new String(plain, 11, nameLen, "UTF-8").trim() : "";
        // CCCD
        card.cccd = new String(plain, 32, 12, "US-ASCII").trim();
        
        // [50] PIN Retry
        card.pinRetry = data[50];
        
        return card;
    }
}