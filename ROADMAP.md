# 🎓 ROADMAP PHÁT TRIỂN GYM SMART CARD - Học Thuật Chuẩn ISO 7816

## 📋 YÊU CẦU HỆ THỐNG

### Yêu cầu chức năng:
- ✅ Lưu thông tin: MemberID, Balance, ExpDate, PackageType, PIN
- ❌ PIN retry counter (0-5 lần)
- ❌ Mã hóa AES cho sensitive data
- ❌ RSA/ECC signing để chống clone
- ❌ Challenge-response authentication
- ✅ Mua hàng/gia hạn yêu cầu PIN
- ✅ Nạp tiền cập nhật Balance
- ✅ Thanh toán offline (trên thẻ)
- ❌ Server app cho online mode
- ❌ Logs lịch sử giao dịch

### Yêu cầu bảo mật:
- ❌ Khóa tạm 10 phút sau 3 lần PIN sai
- ❌ Khóa vĩnh viễn sau 5 lần PIN sai
- ❌ Reset PIN tại quầy (Admin only)
- ❌ Xác thực chữ ký số RSA/ECC

---

## 🚀 PHASE 1: PIN SECURITY (ƯU TIÊN CAO)
**Thời gian: 1-2 tuần | Độ khó: ⭐⭐ | Tính học thuật: CAO**

### Mục tiêu:
Triển khai cơ chế PIN retry counter chuẩn ISO 7816-4 với khóa tạm thời và vĩnh viễn.

### 1.1. Cập nhật Cấu Trúc Thẻ

**Cấu trúc mới (15 bytes):**
```
[UserID(2)] [Balance(4)] [ExpiryDays(2)] [PackageType(1)] [PIN(1)] 
[PINRetry(1)] [LockTimestamp(4)]
```

**Giải thích:**
- `PackageType`: 0=Basic, 1=Silver, 2=Gold, 3=Platinum
- `PINRetry`: 5 → 0 (giảm mỗi lần sai, reset khi đúng)
- `LockTimestamp`: Unix timestamp (4 bytes) khi khóa tạm 10 phút

### 1.2. APDU Commands Mới (Chuẩn ISO 7816-4)

#### **A. VERIFY PIN (0x20) - ISO Standard**
```
CLA  INS  P1  P2  Lc  Data
00   20   00  01  01  [PIN]

Response:
- 9000: PIN correct, reset retry counter
- 63Cx: PIN wrong, x attempts left (e.g., 63C3 = 3 attempts)
- 6983: Authentication blocked (permanently locked)
- 6984: Authentication blocked temporarily (10 min timeout)
```

#### **B. CHANGE PIN (0x24) - ISO Standard**
```
CLA  INS  P1  P2  Lc  Data
00   24   00  01  02  [Old PIN][New PIN]

Response:
- 9000: Success, AES key regenerated
- 6982: Security status not satisfied (need verify first)
- 63Cx: Wrong old PIN
```

#### **C. UNBLOCK PIN (0x2C) - Admin Only**
```
CLA  INS  P1  P2  Lc  Data
00   2C   00  01  08  [Admin Key (8 bytes)]

Response:
- 9000: PIN retry counter reset to 5
- 6982: Admin key invalid
```

### 1.3. Triển Khai JavaCard Applet

**File: `SmartCard.java` (JCIDE)**

```java
package gymcard;

import javacard.framework.*;

public class SmartCard extends Applet {
    // APDU Commands (ISO 7816-4)
    private static final byte INS_READ = (byte) 0xB0;       // READ BINARY
    private static final byte INS_WRITE = (byte) 0xD0;      // UPDATE BINARY
    private static final byte INS_VERIFY_PIN = (byte) 0x20; // VERIFY
    private static final byte INS_CHANGE_PIN = (byte) 0x24; // CHANGE REFERENCE DATA
    private static final byte INS_UNBLOCK_PIN = (byte) 0x2C; // RESET RETRY COUNTER
    
    // Data structure (15 bytes)
    private byte[] cardData = new byte[15];
    private static final short OFFSET_USER_ID = 0;       // 2 bytes
    private static final short OFFSET_BALANCE = 2;       // 4 bytes
    private static final short OFFSET_EXPIRY = 6;        // 2 bytes
    private static final short OFFSET_PACKAGE = 8;       // 1 byte
    private static final short OFFSET_PIN = 9;           // 1 byte
    private static final short OFFSET_PIN_RETRY = 10;    // 1 byte
    private static final short OFFSET_LOCK_TIME = 11;    // 4 bytes
    
    // Security
    private static final byte MAX_PIN_TRIES = 5;
    private static final byte TEMP_LOCK_TRIES = 3;
    private static final int TEMP_LOCK_DURATION = 600; // 10 minutes (seconds)
    
    // Admin key for unblocking (should be secure in production)
    private static final byte[] ADMIN_KEY = {
        (byte)0x41, (byte)0x44, (byte)0x4D, (byte)0x49,
        (byte)0x4E, (byte)0x4B, (byte)0x45, (byte)0x59
    }; // "ADMINKEY"
    
    private boolean pinVerified = false;
    
    protected SmartCard() {
        // Initialize with default values
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES;
        register();
    }
    
    public static void install(byte[] buffer, short offset, byte length) {
        new SmartCard();
    }
    
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        
        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];
        
        switch (ins) {
            case INS_VERIFY_PIN:
                handleVerifyPIN(apdu);
                break;
            case INS_CHANGE_PIN:
                handleChangePIN(apdu);
                break;
            case INS_UNBLOCK_PIN:
                handleUnblockPIN(apdu);
                break;
            case INS_READ:
                handleRead(apdu);
                break;
            case INS_WRITE:
                handleWrite(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    private void handleVerifyPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        // Check if permanently locked
        if (cardData[OFFSET_PIN_RETRY] == 0) {
            ISOException.throwIt(ISO7816.SW_AUTHENTICATION_BLOCKED); // 6983
        }
        
        // Check temporary lock (10 minutes)
        int currentTime = getCurrentTimestamp();
        int lockTime = Util.getInt(cardData, OFFSET_LOCK_TIME);
        
        if (lockTime > 0 && (currentTime - lockTime) < TEMP_LOCK_DURATION) {
            ISOException.throwIt((short) 0x6984); // Temporarily blocked
        }
        
        // Reset temp lock if timeout passed
        if (lockTime > 0 && (currentTime - lockTime) >= TEMP_LOCK_DURATION) {
            Util.setInt(cardData, OFFSET_LOCK_TIME, 0);
        }
        
        // Verify PIN
        if (buf[ISO7816.OFFSET_CDATA] == cardData[OFFSET_PIN]) {
            // Correct PIN
            cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES; // Reset counter
            Util.setInt(cardData, OFFSET_LOCK_TIME, 0); // Clear lock
            pinVerified = true;
        } else {
            // Wrong PIN
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;
            
            // Apply temporary lock after 3 wrong attempts
            if (cardData[OFFSET_PIN_RETRY] == (MAX_PIN_TRIES - TEMP_LOCK_TRIES)) {
                Util.setInt(cardData, OFFSET_LOCK_TIME, currentTime);
            }
            
            // Return remaining attempts in SW2
            short sw = (short) (0x63C0 | cardData[OFFSET_PIN_RETRY]);
            ISOException.throwIt(sw); // 63Cx where x = attempts left
        }
    }
    
    private void handleChangePIN(APDU apdu) {
        if (!pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        byte oldPIN = buf[ISO7816.OFFSET_CDATA];
        byte newPIN = buf[ISO7816.OFFSET_CDATA + 1];
        
        if (oldPIN != cardData[OFFSET_PIN]) {
            cardData[OFFSET_PIN_RETRY]--;
            short sw = (short) (0x63C0 | cardData[OFFSET_PIN_RETRY]);
            ISOException.throwIt(sw);
        }
        
        // Change PIN
        cardData[OFFSET_PIN] = newPIN;
        pinVerified = false; // Require re-verification
        
        // TODO: Regenerate AES key from new PIN (Phase 2)
    }
    
    private void handleUnblockPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Verify admin key
        if (Util.arrayCompare(buf, ISO7816.OFFSET_CDATA, 
                             ADMIN_KEY, (short) 0, (short) 8) != 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        // Reset PIN retry counter
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_TRIES;
        Util.setInt(cardData, OFFSET_LOCK_TIME, 0);
        pinVerified = false;
    }
    
    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, (short) 15);
        apdu.setOutgoingAndSend((short) 0, (short) 15);
    }
    
    private void handleWrite(APDU apdu) {
        if (!pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes < 15) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short) 0, (short) 15);
    }
    
    // Mock timestamp (real card would use RTC or server time)
    private int getCurrentTimestamp() {
        // In production: use javacard.framework.JCSystem timestamp or external RTC
        // For simulation: increment a counter
        return 0; // Simplified
    }
}
```

### 1.4. Cập Nhật Java Client

**File: `CardData.java`**
```java
public class CardData {
    public int userId;
    public int balance;
    public short expiryDays;
    public byte packageType;     // NEW: 0=Basic, 1=Silver, 2=Gold
    public byte pin;
    public byte pinRetry;        // NEW: 5 → 0
    public int lockTimestamp;    // NEW: Unix timestamp
    public byte dobDay;
    public byte dobMonth;
    public short dobYear;
    
    // Security status
    public boolean isLocked() {
        return pinRetry == 0;
    }
    
    public boolean isTempLocked() {
        if (lockTimestamp == 0) return false;
        long now = System.currentTimeMillis() / 1000;
        return (now - lockTimestamp) < 600; // 10 minutes
    }
    
    public int getRemainingLockSeconds() {
        if (!isTempLocked()) return 0;
        long now = System.currentTimeMillis() / 1000;
        return (int) (600 - (now - lockTimestamp));
    }
}
```

**File: `CardHelper.java` - Thêm methods:**
```java
// ISO 7816-4 standard VERIFY command
public static CommandAPDU buildVerifyPinCommand(byte pin) {
    return new CommandAPDU(0x00, 0x20, 0x00, 0x01, new byte[]{pin});
}

// ISO 7816-4 standard CHANGE REFERENCE DATA
public static CommandAPDU buildChangePinCommand(byte oldPin, byte newPin) {
    return new CommandAPDU(0x00, 0x24, 0x00, 0x01, new byte[]{oldPin, newPin});
}

// RESET RETRY COUNTER (Admin only)
public static CommandAPDU buildUnblockPinCommand(byte[] adminKey) {
    return new CommandAPDU(0x00, 0x2C, 0x00, 0x01, adminKey);
}

// Parse SW code for PIN errors
public static String parsePinStatus(int sw) {
    if (sw == 0x9000) return "PIN Correct";
    if (sw == 0x6983) return "Card Permanently Locked";
    if (sw == 0x6984) return "Card Temporarily Locked (10 min)";
    if ((sw & 0xFFF0) == 0x63C0) {
        int tries = sw & 0x0F;
        return "PIN Wrong - " + tries + " attempts left";
    }
    return "Unknown error: " + Integer.toHexString(sw);
}
```

---

## 🚀 PHASE 2: AES ENCRYPTION (2-3 tuần)
**Độ khó: ⭐⭐⭐⭐ | Tính học thuật: RẤT CAO**

### Mục tiêu:
Mã hóa AES-128 cho sensitive data, khóa AES sinh từ PIN.

### 2.1. Kiến trúc AES

**Dữ liệu cần mã hóa:**
- Balance (4 bytes)
- ExpiryDays (2 bytes)
- PackageType (1 byte)

**Key derivation:**
```
PIN (1 byte) → SHA-256 → AES Key (16 bytes)
```

### 2.2. APDU Commands

#### **ENCRYPT DATA (0xE0)**
```
CLA  INS  P1  P2  Lc  Data
00   E0   00  00  07  [plaintext 7 bytes]

Response: [ciphertext 16 bytes padded]
```

#### **DECRYPT DATA (0xE2)**
```
CLA  INS  P1  P2  Lc  Data
00   E2   00  00  10  [ciphertext 16 bytes]

Response: [plaintext 7 bytes]
```

### 2.3. JavaCard Implementation

```java
import javacard.security.*;
import javacardx.crypto.*;

public class SmartCard extends Applet {
    private AESKey aesKey;
    private Cipher aesCipher;
    
    protected SmartCard() {
        // Initialize AES
        aesKey = (AESKey) KeyBuilder.buildKey(
            KeyBuilder.TYPE_AES, 
            KeyBuilder.LENGTH_AES_128, 
            false
        );
        aesCipher = Cipher.getInstance(
            Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, 
            false
        );
        
        // Generate initial key from default PIN
        regenerateAESKey((byte)0x00);
    }
    
    private void regenerateAESKey(byte pin) {
        // Derive AES key from PIN using SHA-256
        MessageDigest sha = MessageDigest.getInstance(
            MessageDigest.ALG_SHA_256, 
            false
        );
        
        byte[] pinBytes = new byte[32]; // Pad to 32 bytes
        pinBytes[0] = pin;
        // Fill rest with salt/constant
        
        byte[] hash = new byte[32];
        sha.doFinal(pinBytes, (short)0, (short)32, hash, (short)0);
        
        // Use first 16 bytes as AES key
        aesKey.setKey(hash, (short)0);
    }
    
    private void encryptData(byte[] plain, short plainOff, 
                            byte[] cipher, short cipherOff) {
        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
        aesCipher.doFinal(plain, plainOff, (short)16, cipher, cipherOff);
    }
}
```

---

## 🚀 PHASE 3: RSA/ECC SIGNING (3-4 tuần)
**Độ khó: ⭐⭐⭐⭐⭐ | Tính học thuật: RẤT CAO**

### Mục tiêu:
Challenge-response authentication chống clone thẻ.

### 3.1. Kiến trúc

**Key pair trên thẻ:**
- Private key: Lưu trong thẻ, không xuất ra
- Public key: Gửi cho server khi khởi tạo

**Authentication flow:**
```
1. Server → Card: Challenge (8 bytes random)
2. Card: Sign(Challenge) với private key
3. Card → Server: Signature
4. Server: Verify(Signature) với public key
```

### 3.2. APDU Commands

#### **GET PUBLIC KEY (0x40)**
```
CLA  INS  P1  P2  Le
00   40   00  00  00

Response: [Public Key (varies by algorithm)]
- RSA-1024: 128 bytes
- ECC P-256: 64 bytes (compressed)
```

#### **SIGN CHALLENGE (0x42)**
```
CLA  INS  P1  P2  Lc  Data
00   42   00  00  08  [Challenge 8 bytes]

Response: [Signature]
- RSA-1024: 128 bytes
- ECC P-256: 64 bytes
```

### 3.3. JavaCard Implementation

```java
import javacard.security.*;

public class SmartCard extends Applet {
    private KeyPair rsaKeyPair;
    private Signature rsaSigner;
    
    // Option 1: RSA-1024 (simpler, larger)
    protected void initRSA() {
        rsaKeyPair = new KeyPair(
            KeyPair.ALG_RSA, 
            KeyBuilder.LENGTH_RSA_1024
        );
        rsaKeyPair.genKeyPair();
        
        rsaSigner = Signature.getInstance(
            Signature.ALG_RSA_SHA_PKCS1, 
            false
        );
    }
    
    // Option 2: ECC P-256 (smaller, faster)
    protected void initECC() {
        rsaKeyPair = new KeyPair(
            KeyPair.ALG_EC_FP, 
            KeyBuilder.LENGTH_EC_FP_256
        );
        rsaKeyPair.genKeyPair();
        
        rsaSigner = Signature.getInstance(
            Signature.ALG_ECDSA_SHA_256, 
            false
        );
    }
    
    private void handleGetPublicKey(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        RSAPublicKey pubKey = (RSAPublicKey) rsaKeyPair.getPublic();
        
        short len = pubKey.getExponent(buf, (short)0);
        len += pubKey.getModulus(buf, len);
        
        apdu.setOutgoingAndSend((short)0, len);
    }
    
    private void handleSignChallenge(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte numBytes = (byte) apdu.setIncomingAndReceive();
        
        if (numBytes != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        rsaSigner.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
        short sigLen = rsaSigner.sign(
            buf, ISO7816.OFFSET_CDATA, numBytes,
            buf, (short)0
        );
        
        apdu.setOutgoingAndSend((short)0, sigLen);
    }
}
```

---

## 🚀 PHASE 4: SERVER APPLICATION (2-3 tuần)
**Độ khó: ⭐⭐⭐ | Tính học thuật: Trung bình**

### Mục tiêu:
Server app Java để đồng bộ, logging, xác thực.

### 4.1. Kiến trúc

**Technology Stack:**
- **Backend**: Spring Boot REST API
- **Database**: PostgreSQL / MySQL
- **Logging**: SLF4J + Logback
- **Security**: JWT tokens, RSA verification

### 4.2. Database Schema

```sql
CREATE TABLE members (
    member_id INT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    package_type SMALLINT, -- 0=Basic, 1=Silver, 2=Gold
    balance INT DEFAULT 0,
    expiry_date DATE,
    pin_hash VARCHAR(64), -- SHA-256 of PIN
    public_key TEXT, -- RSA/ECC public key
    pin_retry SMALLINT DEFAULT 5,
    is_locked BOOLEAN DEFAULT FALSE,
    lock_timestamp TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transactions (
    txn_id SERIAL PRIMARY KEY,
    member_id INT REFERENCES members(member_id),
    txn_type VARCHAR(20), -- TOPUP, PURCHASE, RENEW
    amount INT,
    description TEXT,
    timestamp TIMESTAMP DEFAULT NOW(),
    staff_id INT,
    approved BOOLEAN DEFAULT TRUE
);

CREATE TABLE check_in_logs (
    log_id SERIAL PRIMARY KEY,
    member_id INT REFERENCES members(member_id),
    check_in_time TIMESTAMP DEFAULT NOW(),
    challenge BYTEA,
    signature BYTEA,
    verified BOOLEAN
);
```

### 4.3. REST API Endpoints

```
POST /api/auth/challenge
  → Generate challenge for check-in
  
POST /api/auth/verify
  → Verify signature from card
  
POST /api/topup
  → Record topup transaction
  
POST /api/purchase
  → Record purchase transaction
  
GET /api/transactions/{memberId}
  → Get transaction history
  
POST /api/admin/reset-pin
  → Reset PIN (Admin only)
```

### 4.4. Spring Boot Controller Example

```java
@RestController
@RequestMapping("/api")
public class GymCardController {
    
    @Autowired
    private MemberService memberService;
    
    @Autowired
    private TransactionService transactionService;
    
    @PostMapping("/auth/challenge")
    public ChallengeResponse generateChallenge(@RequestParam int memberId) {
        byte[] challenge = new byte[8];
        new SecureRandom().nextBytes(challenge);
        
        // Store in Redis with 5-minute expiry
        redisTemplate.opsForValue().set(
            "challenge:" + memberId, 
            challenge, 
            5, 
            TimeUnit.MINUTES
        );
        
        return new ChallengeResponse(challenge);
    }
    
    @PostMapping("/auth/verify")
    public VerifyResponse verifySignature(
        @RequestParam int memberId,
        @RequestBody byte[] signature
    ) {
        Member member = memberService.findById(memberId);
        byte[] challenge = redisTemplate.opsForValue().get("challenge:" + memberId);
        
        // Verify RSA signature
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(member.getPublicKey());
        verifier.update(challenge);
        
        boolean valid = verifier.verify(signature);
        
        if (valid) {
            checkInService.logCheckIn(memberId, challenge, signature);
        }
        
        return new VerifyResponse(valid);
    }
    
    @PostMapping("/topup")
    public TransactionResponse topup(@RequestBody TopupRequest req) {
        Member member = memberService.findById(req.getMemberId());
        member.setBalance(member.getBalance() + req.getAmount());
        memberService.save(member);
        
        Transaction txn = new Transaction();
        txn.setMemberId(req.getMemberId());
        txn.setType("TOPUP");
        txn.setAmount(req.getAmount());
        txn.setStaffId(req.getStaffId());
        transactionService.save(txn);
        
        return new TransactionResponse(txn.getId());
    }
}
```

---

## 📚 TÀI LIỆU THAM KHẢO

### ISO Standards:
- **ISO 7816-4**: APDU commands, PIN management
- **ISO 7816-8**: Security operations (crypto)
- **ISO 9564**: PIN management and security

### JavaCard Specs:
- **JavaCard 3.0.5 API**: javacard.security, javacardx.crypto
- **GlobalPlatform 2.3**: Card lifecycle management

### Crypto Libraries:
- **Bouncy Castle**: Java crypto provider
- **JCA/JCE**: Java Cryptography Architecture

---

## ⏱️ TIMELINE TỔNG THỂ

| Phase | Nội dung | Thời gian | Độ khó |
|-------|----------|-----------|--------|
| 1 | PIN Security | 1-2 tuần | ⭐⭐ |
| 2 | AES Encryption | 2-3 tuần | ⭐⭐⭐⭐ |
| 3 | RSA/ECC Signing | 3-4 tuần | ⭐⭐⭐⭐⭐ |
| 4 | Server App | 2-3 tuần | ⭐⭐⭐ |
| 5 | Testing & Docs | 1 tuần | ⭐⭐ |

**Tổng cộng: 9-13 tuần (2-3 tháng)**

---

## ✅ CHECKLIST HOÀN THÀNH

### Phase 1 - PIN Security:
- [ ] Cập nhật cấu trúc thẻ 15 bytes
- [ ] Implement INS_VERIFY_PIN (0x20) chuẩn ISO
- [ ] Implement INS_CHANGE_PIN (0x24)
- [ ] Implement INS_UNBLOCK_PIN (0x2C)
- [ ] Retry counter logic (5 → 0)
- [ ] Khóa tạm 10 phút sau 3 lần sai
- [ ] Khóa vĩnh viễn sau 5 lần sai
- [ ] Admin unblock UI

### Phase 2 - AES:
- [ ] AES key derivation từ PIN
- [ ] Encrypt/Decrypt sensitive data
- [ ] Regenerate key khi đổi PIN

### Phase 3 - RSA/ECC:
- [ ] Generate key pair on card
- [ ] Export public key
- [ ] Sign challenge
- [ ] Server verify signature

### Phase 4 - Server:
- [ ] Spring Boot REST API
- [ ] PostgreSQL database
- [ ] Transaction logging
- [ ] Challenge-response endpoint

---

## 🎯 ƯU TIÊN NGAY LẬP TỨC

**Làm ngay trong 1 tuần tới:**
1. ✅ Phase 1.1: Cập nhật cấu trúc thẻ (15 bytes)
2. ✅ Phase 1.2: APDU commands chuẩn ISO
3. ✅ Phase 1.3: Implement applet với retry counter
4. ✅ Phase 1.4: Java client parsing

**Lý do:** 
- Đơn giản nhất, nền tảng cho các phase sau
- Tính học thuật cao (ISO 7816-4 standard)
- Dễ demo và test
- Không cần crypto library phức tạp
