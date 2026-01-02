package SmartCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

/**
 * GYM SMART CARD - PHASE 2: FULL SECURITY (FIXED)
 * 
 * CRITICAL FIX: Changed data layout to avoid overlap
 * 
 * Card Structure (64 bytes) - SECURE (PII ENCRYPTED):
 * [0-1]   UserID (2 bytes)
 * [2-33]  Encrypted Block (32 bytes AES-128 ECB NoPadding)
 *          Plaintext layout inside 32 bytes:
 *            - Balance (4)
 *            - ExpiryDays (2)
 *            - DOB Day (1)
 *            - DOB Month (1)
 *            - DOB Year (2)
 *            - NameLen (1)
 *            - FullName (max 21 bytes UTF-8)
 *            - Zero padding
 * [34]    PIN Retry Counter (1 byte)
 * [35-50] PIN Hash (16 bytes) - SHA-256 truncated to 16 bytes
 * [51-63] Reserved zeros
 */
public class SmartCard extends Applet {
    // Data offsets - FIXED
    private static final byte OFFSET_USER_ID = 0;
    private static final byte OFFSET_BALANCE = 2;           // Start of encrypted block
    private static final byte ENC_BLOCK_LEN = 48;           // Expanded to 48 bytes (3 AES blocks)
    private static final byte OFFSET_PIN_RETRY = 50;        // After 48-byte block
    private static final byte OFFSET_PIN_HASH = 51;         // 16-byte PIN hash follows retry
    // PII stored inside encrypted block; no public offsets.
    
    private static final short DATA_SIZE = 80;
    private static final byte MAX_PIN_RETRY = 5;
    private static final byte PIN_HASH_SIZE = 16;
    
    // APDU Instructions
    private static final byte INS_READ = (byte) 0xB0;
    private static final byte INS_WRITE = (byte) 0xD0;
    private static final byte INS_VERIFY_PIN = (byte) 0x20;
    private static final byte INS_CHANGE_PIN = (byte) 0x24;
    private static final byte INS_GET_PUBLIC_KEY = (byte) 0x82;
    private static final byte INS_SIGN_CHALLENGE = (byte) 0x88;
    // Avatar APDUs
    private static final byte INS_AVATAR_WRITE = (byte) 0xC0;
    private static final byte INS_AVATAR_CLEAR = (byte) 0xC3;
    
    // Admin Instructions (no PIN required)
    private static final byte INS_ADMIN_UNLOCK = (byte) 0xAA;  // Reset retry counter
    private static final byte INS_ADMIN_RESET_PIN = (byte) 0xAB;  // Reset PIN without old PIN
    
    // Persistent storage
    private byte[] cardData;
    
    // Cryptographic objects
    private AESKey aesKey;
    private Cipher aesCipher;
    private MessageDigest sha256;
    private KeyPair rsaKeyPair;
    private Signature rsaSignature;
    
    // Transient data
    private boolean pinVerified;
    private byte[] tempBuffer;
    private byte[] avatarStore;
    private short avatarLength;
    private static final short MAX_AVATAR_SIZE = (short) 4096; // 4KB
    
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SmartCard().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public SmartCard() {
        cardData = new byte[DATA_SIZE];
        tempBuffer = JCSystem.makeTransientByteArray((short) 80, JCSystem.CLEAR_ON_DESELECT);
        avatarStore = new byte[MAX_AVATAR_SIZE];
        avatarLength = 0;
        
        Util.arrayFillNonAtomic(cardData, (short) 0, DATA_SIZE, (byte) 0x00);
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
        
        initCrypto();
        pinVerified = false;
    }
    
    private void initCrypto() {
        try {
            aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
            
            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
            rsaKeyPair.genKeyPair();
            
            rsaSignature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            rsaSignature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
            
        } catch (CryptoException e) {
            ISOException.throwIt(ISO7816.SW_FILE_INVALID);
        }
    }
    
    private void deriveAESKeyFromPIN(byte[] pinBytes, short offset, short length) {
        // Derive AES-128 key using SHA-256 over PIN byte array (6 ASCII digits), truncated to 16 bytes
        sha256.reset();
        sha256.doFinal(pinBytes, offset, length, tempBuffer, (short) 0);
        // AESKey expects 16 bytes; implementation uses first 16 bytes of tempBuffer
        aesKey.setKey(tempBuffer, (short) 0);
    }
    
    private void hashPIN(byte[] pinBytes, short pinOffset, short pinLength, byte[] output, short offset) {
        // Hash PIN using SHA-256 over PIN byte array (6 ASCII digits), store first 16 bytes (layout constraint)
        sha256.reset();
        sha256.doFinal(pinBytes, pinOffset, pinLength, tempBuffer, (short) 0);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, output, offset, PIN_HASH_SIZE);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_READ:
                handleRead(apdu);
                break;
            case INS_WRITE:
                handleWrite(apdu);
                break;
            case INS_VERIFY_PIN:
                handleVerifyPIN(apdu);
                break;
            case INS_CHANGE_PIN:
                handleChangePIN(apdu);
                break;
            case INS_GET_PUBLIC_KEY:
                handleGetPublicKey(apdu);
                break;
            case INS_SIGN_CHALLENGE:
                handleSignChallenge(apdu);
                break;
            case INS_AVATAR_WRITE:
                handleAvatarWrite(apdu);
                break;
            case INS_AVATAR_CLEAR:
                handleAvatarClear(apdu);
                break;
            // No dedicated CCCD write; CCCD is inside the 48-byte encrypted block
            case INS_ADMIN_UNLOCK:
                handleAdminUnlock(apdu);
                break;
            case INS_ADMIN_RESET_PIN:
                handleAdminResetPin(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);
        apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
    }
    
    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();
        
        if (bytesRead != DATA_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        boolean isBlankCard = (cardData[OFFSET_USER_ID] == 0) && 
                              (cardData[OFFSET_USER_ID + 1] == 0);
        
        // Allow reset: if writing UserID = 0, always allow (for card reset/delete)
        boolean isResetting = (buf[ISO7816.OFFSET_CDATA] == 0) && 
                              (buf[ISO7816.OFFSET_CDATA + 1] == 0);
        
        if (!isBlankCard && !pinVerified && !isResetting) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        // Copy fields (no public PII; all sensitive inside encrypted 32-byte block)
        // UserID [0-1]
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short) 0, (short) 2);
        // Encrypted block [2-49]
        Util.arrayCopyNonAtomic(buf, (short)(ISO7816.OFFSET_CDATA + OFFSET_BALANCE), cardData, OFFSET_BALANCE, ENC_BLOCK_LEN);
        // PIN retry [50]
        cardData[OFFSET_PIN_RETRY] = buf[(short)(ISO7816.OFFSET_CDATA + OFFSET_PIN_RETRY)];
        // PIN hash [51-66]
        Util.arrayCopyNonAtomic(buf, (short)(ISO7816.OFFSET_CDATA + OFFSET_PIN_HASH), cardData, OFFSET_PIN_HASH, (short) 16);
        // Reserved [67-79] remain zeros
        
        // ✅ Generate new RSA keypair when creating/resetting card
        // This ensures each card has unique RSA identity
        if (isBlankCard || isResetting) {
            rsaKeyPair.genKeyPair();
            rsaSignature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
        }
        
        pinVerified = false;
    }
    
    private void handleVerifyPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        if (cardData[OFFSET_PIN_RETRY] == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        
        // Hash 6-byte PIN from APDU data
        hashPIN(buf, ISO7816.OFFSET_CDATA, (short) 6, tempBuffer, (short) 0);
        
        boolean match = true;
        for (short i = 0; i < PIN_HASH_SIZE; i++) {
            if (tempBuffer[i] != cardData[(short)(OFFSET_PIN_HASH + i)]) {
                match = false;
                break;
            }
        }
        
        if (match) {
            pinVerified = true;
            cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
            deriveAESKeyFromPIN(buf, ISO7816.OFFSET_CDATA, (short) 6);

            // Decrypt the 32-byte encrypted block before returning
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(cardData, OFFSET_BALANCE, ENC_BLOCK_LEN, tempBuffer, (short) 0);

            // Copy all card data to buffer
            Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);

            // Replace encrypted block with decrypted data
            Util.arrayCopyNonAtomic(tempBuffer, (short) 0, buf, OFFSET_BALANCE, ENC_BLOCK_LEN);
            
            apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
        } else {
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;
            ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
        }
    }
    
    private void handleChangePIN(APDU apdu) {
        if (!pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Old PIN: buf[5-10], New PIN: buf[11-16]
        short oldPinOffset = ISO7816.OFFSET_CDATA;
        short newPinOffset = (short)(ISO7816.OFFSET_CDATA + 6);
        
        // Verify old PIN - use tempBuffer[0-15] for PIN hash
        hashPIN(buf, oldPinOffset, (short) 6, tempBuffer, (short) 0);
        boolean match = true;
        for (short i = 0; i < PIN_HASH_SIZE; i++) {
            if (tempBuffer[i] != cardData[(short)(OFFSET_PIN_HASH + i)]) {
                match = false;
                break;
            }
        }
        
        if (!match) {
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;
            ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
        }
        
        // ✅ RE-ENCRYPT 48-byte sensitive block with NEW PIN
        // 1. Decrypt with OLD PIN
        // tempBuffer[0..31]: Used by deriveAESKeyFromPIN (SHA-256 output)
        // tempBuffer[32..79]: Safe zone for decrypted data (48 bytes)
        deriveAESKeyFromPIN(buf, oldPinOffset, (short) 6);
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
        aesCipher.doFinal(cardData, OFFSET_BALANCE, ENC_BLOCK_LEN, tempBuffer, (short) 32);

        // 2. Encrypt with NEW PIN (key derivation writes to tempBuffer[0..31], safe)
        deriveAESKeyFromPIN(buf, newPinOffset, (short) 6);
        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
        aesCipher.doFinal(tempBuffer, (short) 32, ENC_BLOCK_LEN, cardData, OFFSET_BALANCE);
        
        // 3. Update PIN hash - hash to tempBuffer[0..15] then copy to card [35..50]
        hashPIN(buf, newPinOffset, (short) 6, tempBuffer, (short) 0);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, cardData, OFFSET_PIN_HASH, PIN_HASH_SIZE);
        
        // Giữ session hợp lệ với PIN mới
        // aesKey đã được derive từ newPin ở bước 2
        // pinVerified vẫn = true, không cần reset
        // pinVerified = false; 
    }
    
    private void handleGetPublicKey(APDU apdu) {
        RSAPublicKey pubKey = (RSAPublicKey) rsaKeyPair.getPublic();
        
        byte[] buf = apdu.getBuffer();
        short offset = 0;
        
        short modulusLen = pubKey.getModulus(buf, offset);
        offset += modulusLen;
        
        short exponentLen = pubKey.getExponent(buf, offset);
        offset += exponentLen;
        
        apdu.setOutgoingAndSend((short) 0, offset);
        // Hint GC to collect any temporary objects if needed
        JCSystem.requestObjectDeletion();
    }
    
    private void handleSignChallenge(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 32) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        rsaSignature.sign(buf, ISO7816.OFFSET_CDATA, lc, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, (short) 128);
        // Hint GC to collect any temporary objects if needed
        JCSystem.requestObjectDeletion();
    }
    
    /**
     * Admin unlock - Reset retry counter without PIN verification
     * Command: 00 AA 00 00
     * Response: 90 00 (success)
     */
    private void handleAdminUnlock(APDU apdu) {
        // Admin privilege: reset retry counter without authentication
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
        // Note: pinVerified stays false - this is just unlock, not authentication
    }
    
    /**
     * Admin reset PIN - Change PIN without knowing old PIN
     * Command: 00 AB 00 00 06 [6-byte new PIN]
     * Response: 90 00 (success)
     * 
     * WARNING: Balance/Expiry will be re-encrypted with NEW PIN.
     * Old encrypted data will become unreadable.
     */
    private void handleAdminResetPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // buf[OFFSET_CDATA..OFFSET_CDATA+5] chứa PIN mới dạng ASCII
        short newPinOffset = ISO7816.OFFSET_CDATA;

        // 1) Tính SHA-256 của PIN mới và lưu vào tempBuffer
        sha256.reset();
        sha256.doFinal(buf, newPinOffset, (short) 6, tempBuffer, (short) 0);

        // 2) Cập nhật 16 byte PIN hash mới vào cardData
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, cardData, OFFSET_PIN_HASH, PIN_HASH_SIZE);

        // 3) Reset bộ đếm lần thử PIN
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;

        // Lưu ý: Khối dữ liệu mã hóa [2-33] vẫn đang mã hóa bằng PIN cũ.
        // Desktop app phải ghi đè dữ liệu hợp lệ ngay sau lệnh này.
    }

    /**
     * Write avatar chunk into EEPROM storage.
     * P1/P2: offset (big-endian). LC: chunk size. CDATA: chunk bytes.
     */
    private void handleAvatarWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        short offset = Util.getShort(buf, ISO7816.OFFSET_P1);
        if (lc <= 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short end = (short) (offset + lc);
        if (end > MAX_AVATAR_SIZE) {
            ISOException.throwIt(ISO7816.SW_FILE_FULL);
        }
        // Store avatar in plaintext (non-sensitive); fast and simple
        Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, avatarStore, offset, lc);
        if (end > avatarLength) avatarLength = end;
    }

    /**
     * Clear avatar storage
     */
    private void handleAvatarClear(APDU apdu) {
        Util.arrayFillNonAtomic(avatarStore, (short)0, MAX_AVATAR_SIZE, (byte)0x00);
        avatarLength = 0;
    }

    // CCCD is included within the main encrypted block; no separate handler
}