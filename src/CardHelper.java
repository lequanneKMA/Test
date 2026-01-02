import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helpers to build/parse APDU commands for gym smart card.
 * 
 * PHASE 2 - Security Enhanced (JavaCard 3.0.4+ Compatible):
 * - AES-128 encryption for Balance & Expiry
 * - SHA-256 hashing for PIN verification
 * - RSA-1024 for card authentication
 * 
 * APDU Instructions:
 *  0xB0 = READ (read card data - encrypted fields remain encrypted)
 *  0xD0 = WRITE (write card data - client must encrypt before sending)
 *  0x20 = VERIFY PIN (hash PIN with SHA-256, verify on card)
 *  0x24 = CHANGE PIN (change PIN, requires old PIN verification)
 *  0x82 = GET PUBLIC KEY (export RSA public key for authentication)
 *  0x88 = SIGN CHALLENGE (sign challenge with RSA private key)
 */
public class CardHelper {
    public static final byte INS_READ = (byte) 0xB0;
    public static final byte INS_WRITE = (byte) 0xD0;
    public static final byte INS_VERIFY_PIN = (byte) 0x20;
    public static final byte INS_CHANGE_PIN = (byte) 0x24;
    public static final byte INS_GET_PUBLIC_KEY = (byte) 0x82;
    public static final byte INS_SIGN_CHALLENGE = (byte) 0x88;
    public static final byte INS_ADMIN_UNLOCK = (byte) 0xAA;
    public static final byte INS_ADMIN_RESET_PIN = (byte) 0xAB;
    public static final byte INS_AVATAR_WRITE = (byte) 0xC0;
    public static final byte INS_AVATAR_CLEAR = (byte) 0xC3;

    /**
     * Build: 00 B0 00 00 40 (read 64 bytes)
     * ISO 7816-4 READ BINARY command
     */
    public static CommandAPDU buildReadCommand() {
        return new CommandAPDU(0x00, INS_READ, 0x00, 0x00, 80);
    }

    /**
     * Build: 00 D0 00 00 40 [data...] (write 64 bytes)
     * 
     * New Card Structure (64 bytes):
     * [0-1]   UserID (2 bytes)
     * [2-33]  Encrypted Area (32 bytes) AES-128/ECB/NoPadding of:
     *           balance(4) | expiryDays(2) | dobDay(1) | dobMonth(1) | dobYear(2) | nameLen(1) | name(<=21) | pad
     * [34]    PIN Retry Counter (1 byte)
     * [35-50] PIN Hash (16 bytes, SHA-256 truncated)
     * [51-63] Reserved (zeros)
     */
    public static CommandAPDU buildWriteCommand(CardData card) throws Exception {
        byte[] data = CryptoHelper.buildCardData(
            card.userId,
            card.balance,
            card.expiryDays,
            card.pin,
            card.pinRetry,
            card.dobDay,
            card.dobMonth,
            card.dobYear,
            card.fullName != null ? card.fullName : "",
            card.cccd != null ? card.cccd : ""
        );
        
        return new CommandAPDU(0x00, INS_WRITE, 0x00, 0x00, data);
    }

    /**
     * Build: 00 20 00 00 06 [6-byte ASCII PIN] - ISO 7816-4 VERIFY command
     * Verify the PIN on card.
     * Response: 64 bytes card data if correct (SW=9000)
     */
    public static CommandAPDU buildVerifyPinCommand(String pin) {
        byte[] pinData = pin.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (pinData.length != 6) {
            throw new IllegalArgumentException("PIN must be exactly 6 digits");
        }
        return new CommandAPDU(0x00, INS_VERIFY_PIN, 0x00, 0x00, pinData, 80);
    }
    
    /**
     * Build: 00 24 00 00 0C [old PIN 6B][new PIN 6B] - ISO 7816-4 CHANGE REFERENCE DATA
     * Change PIN (requires prior PIN verification)
     */
    public static CommandAPDU buildChangePinCommand(String oldPin, String newPin) {
        byte[] oldPinData = oldPin.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] newPinData = newPin.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (oldPinData.length != 6 || newPinData.length != 6) {
            throw new IllegalArgumentException("PINs must be exactly 6 digits each");
        }
        byte[] data = new byte[12];
        System.arraycopy(oldPinData, 0, data, 0, 6);
        System.arraycopy(newPinData, 0, data, 6, 6);
        return new CommandAPDU(0x00, INS_CHANGE_PIN, 0x00, 0x00, data);
    }
    
    /**
     * Build: 00 AA 00 00 - Admin unlock (reset retry counter)
     * No PIN verification required - admin privilege
     */
    public static CommandAPDU buildAdminUnlockCommand() {
        return new CommandAPDU(0x00, INS_ADMIN_UNLOCK, 0x00, 0x00);
    }
    
    /**
     * Build: 00 AB 00 00 06 [new PIN 6B] - Admin reset PIN
     * Change PIN without requiring old PIN - admin privilege
     * WARNING: Changes AES key, invalidating encrypted balance/expiry
     */
    public static CommandAPDU buildAdminResetPinCommand(String newPin) {
        byte[] pinData = newPin.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (pinData.length != 6) {
            throw new IllegalArgumentException("PIN must be exactly 6 digits");
        }
        return new CommandAPDU(0x00, INS_ADMIN_RESET_PIN, 0x00, 0x00, pinData);
    }
    
    /**
     * Build: 00 82 00 00 83 - Get RSA public key
     * Response: [Modulus 128 bytes][Exponent 3 bytes] = 131 bytes
     */
    public static CommandAPDU buildGetPublicKeyCommand() {
        return new CommandAPDU(0x00, INS_GET_PUBLIC_KEY, 0x00, 0x00, 131);
    }
    
    /**
     * Build: 00 88 00 00 20 [32-byte challenge] - Sign challenge for authentication
     * Response: [128-byte RSA signature]
     */
    public static CommandAPDU buildSignChallengeCommand(byte[] challenge) {
        if (challenge == null || challenge.length != 32) {
            throw new IllegalArgumentException("Challenge must be exactly 32 bytes");
        }
        return new CommandAPDU(0x00, INS_SIGN_CHALLENGE, 0x00, 0x00, challenge, 128);
    }

    /**
     * Avatar: Clear stored image on card
     */
    public static CommandAPDU buildAvatarClearCommand() {
        return new CommandAPDU(0x00, INS_AVATAR_CLEAR, 0x00, 0x00);
    }

    /**
     * Avatar: Write chunk to card at offset (P1/P2 = offset high/low)
     */
    public static CommandAPDU buildAvatarWriteChunk(int offset, byte[] chunk) {
        if (offset < 0 || offset > 0xFFFF) throw new IllegalArgumentException("Offset must be 0..65535");
        if (chunk == null || chunk.length == 0) throw new IllegalArgumentException("Chunk cannot be empty");
        int p1 = (offset >> 8) & 0xFF;
        int p2 = offset & 0xFF;
        return new CommandAPDU(0x00, INS_AVATAR_WRITE, p1, p2, chunk);
    }

    // No CCCD write APDU: CCCD is inside the main encrypted block

    /**
     * Parse response from READ command (64 bytes) - PII-SAFE VIEW
     * 
     * Parses only non-PII: UserID and PIN retry counter. PII (name, DOB)
     * is not parsed and will be blank to reflect on-card PII removal.
     * Balance & Expiry will be set to -1 (encrypted, need PIN to view)
     * 
     * Use parseReadResponse(data, pin) to decrypt balance/expiry only.
     */
    public static CardData parseReadResponse(byte[] data) throws Exception {
        if (data == null || data.length != 80) {
            throw new IllegalArgumentException("Invalid data length: expected 80 bytes");
        }
        
        CardData card = new CardData();
        
        // [0-1] User ID (unencrypted)
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // Encrypted area - skip, set to -1
        card.balance = -1;
        card.expiryDays = -1;
        
        // [50] PIN retry counter (unencrypted)
        card.pinRetry = data[50];
        
        // PII removed: do not expose DOB or FullName from card
        card.dobDay = 0;
        card.dobMonth = 0;
        card.dobYear = 0;
        card.fullName = "";
        card.cccd = "";
        
        card.pin = null; // Admin doesn't know PIN
        return card;
    }
    
    /**
     * ✅ Parse ENCRYPTED response from READ command
     * 
     * @param data 64-byte encrypted response from card
     * @param pin PIN to decrypt Balance & Expiry (6-digit string)
     */
    public static CardData parseReadResponse(byte[] data, String pin) throws Exception {
        return CryptoHelper.parseEncryptedCardData(data, pin);
    }
    
    /**
     * ✅ Parse DECRYPTED response from VERIFY PIN command (new layout)
     * 
     * @param response Response APDU from VERIFY_PIN
     * @param pin PIN value (for storing in CardData.pin field, 6-digit string)
     * @throws Exception if PIN is wrong or other errors
     */
    public static CardData parseVerifyPinResponse(ResponseAPDU response, String pin) throws Exception {
        int sw = response.getSW();
        
        if (sw == 0x9000) {
            // PIN correct - card returns 64 bytes of DECRYPTED data
            byte[] data = response.getData();
            if (data == null || data.length < 80) {
                throw new Exception("Invalid response data length: " + (data != null ? data.length : 0));
            }
            // ✅ Use parseDecryptedCardData - data is already plaintext from card
            return CryptoHelper.parseDecryptedCardData(data, pin);
        } else if ((sw & 0xFF00) == 0x6300) {
            // Wrong PIN - extract retry counter
            int retriesLeft = sw & 0x0F;
            throw new Exception("Wrong PIN! Retries left: " + retriesLeft);
        } else if (sw == 0x6983) {
            throw new Exception("Card is LOCKED!");
        } else if (sw == 0x6982) {
            throw new Exception("Security status not satisfied (verify PIN first)");
        } else {
            throw new Exception("Verify PIN failed: 0x" + Integer.toHexString(sw).toUpperCase());
        }
    }
    
    /**
     * Parse PIN verification status from SW code (for display)
     * 
     * @param sw Status Word from VERIFY PIN response
     * @return Human-readable status message
     */
    public static String parsePinStatus(int sw) {
        if (sw == 0x9000) {
            return "PIN Correct";
        } else if (sw == 0x6983) {
            return "Card Permanently Locked (0 attempts left)";
        } else if ((sw & 0xFFF0) == 0x63C0) {
            int tries = sw & 0x0F;
            return "PIN Wrong - " + tries + "/5 attempts remaining";
        } else if (sw == 0x6982) {
            return "Security status not satisfied (verify PIN first)";
        } else {
            return "Unknown error: 0x" + Integer.toHexString(sw).toUpperCase();
        }
    }
    
    /**
     * Convert 6-digit PIN string to byte (for storage/transmission)
     */
    public static byte pinStringToByte(String pin6digit) {
        if (pin6digit == null || !pin6digit.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must be 6 digits");
        }
        int pinValue = Integer.parseInt(pin6digit);
        return (byte) (pinValue % 256);
    }
    
    /**
     * Check if response indicates success
     */
    public static boolean isSuccess(ResponseAPDU response) {
        return (response.getSW() & 0xFF00) == 0x9000;
    }
    
    /**
     * Get retry counter from error response
     * Returns -1 if not a retry counter error
     */
    public static int getRetryCounter(ResponseAPDU response) {
        int sw = response.getSW();
        if ((sw & 0xFF00) == 0x6300) {
            return sw & 0x0F;
        }
        return -1;
    }

    /**
     * Get APDU as hex string for debugging.
     */
    public static String toHexCommand(CommandAPDU apdu) {
        byte[] bytes = apdu.getBytes();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    
    /**
     * Format bytes as hex string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}