# 📋 BÁO CÁO KỸ THUẬT: HỆ THỐNG THẺ THÔNG MINH QUẢN LÝ PHÒNG GYM

## 📑 MỤC LỤC
1. [Tổng Quan Hệ Thống](#1-tổng-quan-hệ-thống)
2. [Cách Thức Kết Nối Thẻ Với Host](#2-cách-thức-kết-nối-thẻ-với-host)
3. [Cơ Chế Làm Việc Giữa Host và CSDL](#3-cơ-chế-làm-việc-giữa-host-và-csdl)
4. [Cơ Chế Xác Thực và Mã Hóa](#4-cơ-chế-xác-thực-và-mã-hóa)
5. [Cấu Trúc Dữ Liệu](#5-cấu-trúc-dữ-liệu)
6. [Các Chức Năng Chính](#6-các-chức-năng-chính)

---

## 1. TỔNG QUAN HỆ THỐNG

### 1.1 Kiến Trúc Tổng Thể

```
┌─────────────────┐     PC/SC      ┌─────────────────┐
│   Java Card     │◄──────────────►│   Host PC       │
│   (SmartCard)   │   ISO 7816-4   │   (Java App)    │
│                 │    APDU        │                 │
│  • AES-128      │                │  • PcscClient   │
│  • SHA-256      │                │  • CardHelper   │
│  • RSA-1024     │                │  • CryptoHelper │
└─────────────────┘                └────────┬────────┘
                                            │
                                            │ JDBC
                                            ▼
                                   ┌─────────────────┐
                                   │    SQLite DB    │
                                   │  (members.db)   │
                                   │                 │
                                   │  • members      │
                                   │  • transactions │
                                   └─────────────────┘
```

### 1.2 Các Thành Phần Chính

| Thành Phần | Mô Tả | File |
|------------|-------|------|
| **Java Card Applet** | Chương trình chạy trên thẻ, xử lý mã hóa/giải mã | `SmartCard.java` |
| **PC/SC Client** | Kết nối thẻ qua đầu đọc | `PcscClient.java` |
| **Card Helper** | Xây dựng và phân tích lệnh APDU | `CardHelper.java` |
| **Crypto Helper** | Xử lý mã hóa phía Host | `CryptoHelper.java` |
| **Members DAO** | Truy xuất CSDL thành viên | `MembersDao.java` |
| **Transactions DAO** | Quản lý giao dịch | `TransactionsDao.java` |

---

## 2. CÁCH THỨC KẾT NỐI THẺ VỚI HOST

### 2.1 Giao Thức PC/SC (Personal Computer/Smart Card)

Hệ thống sử dụng **Java Smart Card I/O API** (`javax.smartcardio`) để giao tiếp với thẻ thông qua giao thức PC/SC.

#### 2.1.1 Luồng Kết Nối

```
┌──────────────────────────────────────────────────────────────────┐
│                        QUY TRÌNH KẾT NỐI                         │
├──────────────────────────────────────────────────────────────────┤
│  1. Khởi tạo TerminalFactory                                     │
│         ↓                                                        │
│  2. Liệt kê danh sách đầu đọc (CardTerminal)                    │
│         ↓                                                        │
│  3. Kiểm tra thẻ có sẵn trên đầu đọc                            │
│         ↓                                                        │
│  4. Kết nối với thẻ (card.connect("*"))                         │
│         ↓                                                        │
│  5. Lấy kênh giao tiếp cơ bản (BasicChannel)                    │
│         ↓                                                        │
│  6. Truyền lệnh APDU qua channel.transmit()                     │
└──────────────────────────────────────────────────────────────────┘
```

#### 2.1.2 Code Kết Nối (PcscClient.java)

```java
public class PcscClient implements AutoCloseable {
    private final TerminalFactory terminalFactory;
    private CardTerminal terminal;
    private Card card;
    private CardChannel channel;

    public PcscClient() {
        this.terminalFactory = TerminalFactory.getDefault();
    }

    // Liệt kê tất cả đầu đọc thẻ
    public List<CardTerminal> listTerminals() throws CardException {
        return terminalFactory.terminals().list();
    }

    // Kết nối với thẻ đầu tiên có sẵn
    public PcscClient connectFirstPresentOrFirst() throws Exception {
        List<CardTerminal> terminals = listTerminals();
        if (terminals.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy đầu đọc thẻ");
        }

        // Ưu tiên đầu đọc đã có thẻ
        for (CardTerminal t : terminals) {
            if (t.isCardPresent()) {
                return connect(t);
            }
        }
        return connect(terminals.get(0));
    }

    // Kết nối với đầu đọc cụ thể
    public PcscClient connect(CardTerminal terminal) throws CardException {
        this.terminal = terminal;
        this.card = terminal.connect("*");  // "*" = giao thức tự động (T=0 hoặc T=1)
        this.channel = card.getBasicChannel();
        return this;
    }

    // Truyền lệnh APDU
    public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
        return channel.transmit(apdu);
    }
}
```

### 2.2 Giao Thức APDU (Application Protocol Data Unit)

#### 2.2.1 Cấu Trúc Command APDU (Host → Card)

```
┌─────┬─────┬─────┬─────┬─────┬────────────┬─────┐
│ CLA │ INS │ P1  │ P2  │ Lc  │   Data     │ Le  │
├─────┼─────┼─────┼─────┼─────┼────────────┼─────┤
│ 1B  │ 1B  │ 1B  │ 1B  │ 1B  │ Lc bytes   │ 1B  │
└─────┴─────┴─────┴─────┴─────┴────────────┴─────┘

CLA = Class byte (00 = tiêu chuẩn)
INS = Instruction byte (mã lệnh)
P1, P2 = Tham số
Lc = Độ dài dữ liệu gửi đi
Data = Dữ liệu gửi đi
Le = Độ dài dữ liệu mong đợi nhận về
```

#### 2.2.2 Cấu Trúc Response APDU (Card → Host)

```
┌────────────────────┬─────┬─────┐
│       Data         │ SW1 │ SW2 │
├────────────────────┼─────┼─────┤
│    0-256 bytes     │ 1B  │ 1B  │
└────────────────────┴─────┴─────┘

SW1-SW2 = Status Word (mã trạng thái)
  • 90 00 = Thành công
  • 63 Cx = Sai PIN, còn x lần thử
  • 69 83 = Thẻ bị khóa
  • 69 82 = Chưa xác thực PIN
```

#### 2.2.3 Danh Sách Các Lệnh APDU

| INS | Tên Lệnh | Mô Tả | Yêu Cầu PIN |
|-----|----------|-------|-------------|
| `0xB0` | READ | Đọc dữ liệu thẻ (80 bytes) | Không |
| `0xD0` | WRITE | Ghi dữ liệu lên thẻ (80 bytes) | Có* |
| `0x20` | VERIFY_PIN | Xác thực mã PIN | Không |
| `0x24` | CHANGE_PIN | Đổi mã PIN | Có |
| `0x82` | GET_PUBLIC_KEY | Lấy RSA public key | Không |
| `0x88` | SIGN_CHALLENGE | Ký challenge với RSA | Không |
| `0xAA` | ADMIN_UNLOCK | Mở khóa thẻ (admin) | Không |
| `0xAB` | ADMIN_RESET_PIN | Reset PIN (admin) | Không |
| `0xC0` | AVATAR_WRITE | Ghi ảnh đại diện | Không |
| `0xC3` | AVATAR_CLEAR | Xóa ảnh đại diện | Không |

*WRITE yêu cầu PIN trừ trường hợp thẻ trống hoặc reset về 0.

Lưu ý về cột "Yêu Cầu PIN":
- "Không" nghĩa là lệnh không đòi hỏi trạng thái đã-xác-thực trước khi gọi. `VERIFY_PIN (0x20)` chính là lệnh dùng để thực hiện việc xác thực, nên bản thân nó không yêu cầu đã xác thực.
- `READ (0xB0)` có thể đọc 80 byte dữ liệu bất kỳ lúc nào, nhưng các trường nhạy cảm nằm trong 48 byte payload được mã hóa. Nếu chưa gửi `VERIFY_PIN`, host chỉ thấy dữ liệu mã hóa; sau khi `VERIFY_PIN` thành công, thẻ trả về bản đã giải mã (trong response của VERIFY) để ứng dụng hiển thị.
- Các lệnh như `WRITE`, `CHANGE_PIN`… yêu cầu PIN (đã xác thực) vì chúng thay đổi trạng thái dữ liệu trên thẻ; nếu chưa xác thực, thẻ sẽ trả mã trạng thái 69 82 (Security condition not satisfied).

### 2.3 Select Applet

Trước khi giao tiếp, host phải chọn applet trên thẻ:

```java
// AID (Application Identifier): 26 12 20 03 03 00
byte[] AID = new byte[]{(byte)0x26, (byte)0x12, (byte)0x20, 
                         (byte)0x03, (byte)0x03, (byte)0x00};

// Command: 00 A4 04 00 06 [AID]
CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, AID);
ResponseAPDU response = pcsc.transmit(selectCmd);

if (response.getSW() == 0x9000) {
    // Applet đã được chọn thành công
}
```

---

## 3. CƠ CHẾ LÀM VIỆC GIỮA HOST VÀ CSDL

### 3.1 Kiến Trúc CSDL

Hệ thống sử dụng **SQLite** với driver `sqlite-jdbc` để lưu trữ dữ liệu phía server.

#### 3.1.1 Sơ Đồ Bảng (Entity Relationship)

```
┌─────────────────────────────────────────────────────────────┐
│                         MEMBERS                              │
├─────────────────────────────────────────────────────────────┤
│ id              INTEGER PRIMARY KEY  -- Khớp với UserID thẻ │
│ full_name       TEXT                                         │
│ balance_vnd     INTEGER DEFAULT 0                           │
│ birthdate       TEXT                  -- Format: YYYY-MM-DD │
│ expiry_date     TEXT                  -- Ngày hết hạn tập   ││
│ rsa_modulus     TEXT                  -- RSA modulus (hex)  │
│ rsa_exponent    TEXT                  -- RSA exponent (hex) │
│ pinretry        INTEGER DEFAULT 5     -- Số lần thử PIN     │
│ cccd            TEXT                  -- Căn cước công dân  │
│ avatar_data     BLOB                  -- Ảnh đại diện       │
│ last_checkin_date TEXT                -- Ngày check-in cuối │
│ created_at      TEXT                  -- Thời gian tạo      │
│ updated_at      TEXT                  -- Cập nhật cuối      │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       TRANSACTIONS                           │
├─────────────────────────────────────────────────────────────┤
│ id              INTEGER PRIMARY KEY AUTOINCREMENT            │
│ member_id       INTEGER NOT NULL      -- FK → members.id    │
│ type            TEXT NOT NULL         -- TOPUP/PURCHASE/RENEW│
│ amount          INTEGER NOT NULL      -- Số tiền (VND)      │
│ items           TEXT                  -- JSON chi tiết      │
│ payment_method  TEXT                  -- Phương thức TT     │
│ created_at      TEXT                  -- Thời gian giao dịch │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Data Access Object (DAO) Pattern

Hệ thống sử dụng pattern DAO để tách biệt logic truy xuất CSDL:

```java
public class MembersDao {
    private final String dbUrl = "jdbc:sqlite:members.db";

    // Lấy thông tin thành viên theo ID
    public MemberRecord getByUserId(int userId) throws SQLException {
        String sql = "SELECT * FROM members WHERE id = ?";
        try (Connection conn = getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        }
    }

    // Thêm mới hoặc cập nhật thành viên (UPSERT)
    public void upsert(MemberRecord m) throws SQLException {
        String sql = "INSERT INTO members (...) VALUES (...) " +
                     "ON CONFLICT(id) DO UPDATE SET ...";
        // Sử dụng SQLite UPSERT syntax
    }
}
```

### 3.3 Luồng Đồng Bộ Dữ Liệu (Thẻ ↔ CSDL)

#### 3.3.1 Khi Tạo Thẻ Mới

```
┌──────────────────────────────────────────────────────────────┐
│                   QUY TRÌNH TẠO THẺ MỚI                      │
├──────────────────────────────────────────────────────────────┤
│  1. Admin nhập thông tin: Tên, DOB, CCCD, Balance, Expiry   │
│         ↓                                                    │
│  2. Sinh UserID mới = max(id) + 1 từ DB                     │
│         ↓                                                    │
│  3. Sinh PIN ngẫu nhiên 6 số                                 │
│         ↓                                                    │
│  4. Mã hóa dữ liệu với PIN (AES-128)                        │
│         ↓                                                    │
│  5. Ghi 80 bytes xuống thẻ (WRITE APDU)                     │
│         ↓                                                    │
│  6. Đọc RSA Public Key từ thẻ                               │
│         ↓                                                    │
│  7. Lưu thông tin vào DB (upsert)                           │
│         ↓                                                    │
│  8. Lưu RSA key vào DB                                       │
└──────────────────────────────────────────────────────────────┘
```

#### 3.3.2 Khi Check-in (Quẹt Thẻ)

```
┌──────────────────────────────────────────────────────────────┐
│                    QUY TRÌNH CHECK-IN                        │
├──────────────────────────────────────────────────────────────┤
│  1. Đọc thẻ → Lấy UserID                                    │
│         ↓                                                    │
│  2. User nhập PIN                                            │
│         ↓                                                    │
│  3. VERIFY_PIN APDU → Nhận 80 bytes đã giải mã              │
│         ↓                                                    │
│  4. Parse dữ liệu: Balance, Expiry, Tên, ...                │
│         ↓                                                    │
│  5. Kiểm tra còn hạn tập không                              │
│         ↓                                                    │
│  6. Trừ 1 ngày expiry, ghi lại xuống thẻ                    │
│         ↓                                                    │
│  7. Cập nhật expiry_date và last_checkin_date vào DB        │
└──────────────────────────────────────────────────────────────┘
```

### 3.4 Xử Lý Transaction

```java
public class TransactionsDao {
    
    // Ghi log nạp tiền
    public void logTopup(int memberId, int amount, String paymentMethod) {
        String sql = "INSERT INTO transactions (member_id, type, amount, ...) " +
                     "VALUES (?, 'TOPUP', ?, ...)";
    }

    // Ghi log mua hàng
    public void logPurchase(int memberId, List<CartItem> items, int totalPrice) {
        String sql = "INSERT INTO transactions (member_id, type, amount, items, ...) " +
                     "VALUES (?, 'PURCHASE', ?, ?, ...)";
    }

    // Ghi log gia hạn
    public void logRenew(int memberId, int daysAdded, int price) {
        String sql = "INSERT INTO transactions (member_id, type, amount, items, ...) " +
                     "VALUES (?, 'RENEW', ?, ?, ...)";
    }
}
```

---

## 4. CƠ CHẾ XÁC THỰC VÀ MÃ HÓA

### 4.1 Tổng Quan Bảo Mật

Hệ thống sử dụng **3 lớp bảo mật**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    CÁC LỚP BẢO MẬT                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  LỚP 1: PIN AUTHENTICATION                              │   │
│  │  • SHA-256 hash PIN                                      │   │
│  │  • Giới hạn 5 lần thử                                   │   │
│  │  • Khóa thẻ vĩnh viễn khi hết lượt                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  LỚP 2: AES-128 ENCRYPTION                              │   │
│  │  • Mã hóa dữ liệu nhạy cảm (Balance, Name, DOB, CCCD)   │   │
│  │  • Key được derive từ PIN (SHA-256)                     │   │
│  │  • ECB mode, No Padding                                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  LỚP 3: RSA-1024 AUTHENTICATION                         │   │
│  │  • Mỗi thẻ có keypair riêng                             │   │
│  │  • Challenge-Response để xác thực thẻ thật              │   │
│  │  • Private key không bao giờ rời khỏi thẻ              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Xác Thực PIN (Chi Tiết)

#### 4.2.1 Lưu Trữ PIN Trên Thẻ

PIN được lưu dưới dạng **hash SHA-256 truncated 16 bytes**, không lưu plaintext:

```java
// Phía Host: Tạo PIN hash
public static byte[] hashPIN(String pin) throws Exception {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    byte[] pinBytes = pin.getBytes("ASCII");  // "123456" → 6 bytes
    byte[] fullHash = sha256.digest(pinBytes); // 32 bytes
    
    // Truncate về 16 bytes (giới hạn bộ nhớ thẻ)
    byte[] truncatedHash = new byte[16];
    System.arraycopy(fullHash, 0, truncatedHash, 0, 16);
    return truncatedHash;
}
```

```java
// Phía Card: Verify PIN
private void handleVerifyPIN(APDU apdu) {
    byte[] buf = apdu.getBuffer();
    
    // Kiểm tra còn lượt thử không
    if (cardData[OFFSET_PIN_RETRY] == 0) {
        ISOException.throwIt((short) 0x6983);  // Card locked
    }
    
    // Hash PIN từ APDU
    hashPIN(buf, ISO7816.OFFSET_CDATA, (short) 6, tempBuffer, (short) 0);
    
    // So sánh với hash lưu trên thẻ
    boolean match = true;
    for (short i = 0; i < PIN_HASH_SIZE; i++) {
        if (tempBuffer[i] != cardData[(short)(OFFSET_PIN_HASH + i)]) {
            match = false;
            break;
        }
    }
    
    if (match) {
        pinVerified = true;
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;  // Reset retry counter
        
        // Giải mã và trả về dữ liệu
        deriveAESKeyFromPIN(buf, ISO7816.OFFSET_CDATA, (short) 6);
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
        aesCipher.doFinal(cardData, OFFSET_BALANCE, ENC_BLOCK_LEN, tempBuffer, (short) 0);
        
        // Trả về 80 bytes với block đã giải mã
        apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
    } else {
        cardData[OFFSET_PIN_RETRY]--;  // Giảm retry counter
        pinVerified = false;
        // Trả về SW = 63Cx (x = số lần còn lại)
        ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
    }
}
```

#### 4.2.2 Luồng Xác Thực PIN

```
         HOST                                    CARD
           │                                       │
           │  1. VERIFY_PIN [6-byte PIN]           │
           │──────────────────────────────────────►│
           │                                       │
           │                              ┌────────┴────────┐
           │                              │ Hash PIN (SHA-256)
           │                              │ So sánh với stored hash
           │                              │                 │
           │                              │  ┌──────────────┴───┐
           │                              │  │ PIN đúng?        │
           │                              │  └──────────────────┘
           │                              │    │YES        │NO
           │                              │    ▼           ▼
           │                              │ Reset retry  Giảm retry
           │                              │ Decrypt data  Return 63Cx
           │                              └────────┬────────┘
           │                                       │
           │  2a. [80 bytes decrypted] + 9000      │
           │◄──────────────────────────────────────│ (PIN đúng)
           │                                       │
           │  2b. SW = 63Cx (x = retry còn lại)   │
           │◄──────────────────────────────────────│ (PIN sai)
           │                                       │
           │  2c. SW = 6983 (card locked)         │
           │◄──────────────────────────────────────│ (retry = 0)
```

### 4.3 Mã Hóa AES-128 (Chi Tiết)

#### 4.3.1 Key Derivation (Từ PIN)

```java
// Derive AES key từ PIN sử dụng SHA-256
private void deriveAESKeyFromPIN(byte[] pinBytes, short offset, short length) {
    // Hash PIN: SHA-256("123456") → 32 bytes
    sha256.reset();
    sha256.doFinal(pinBytes, offset, length, tempBuffer, (short) 0);
    
    // Lấy 16 bytes đầu làm AES key
    aesKey.setKey(tempBuffer, (short) 0);
}
```

**Ví dụ:**
```
PIN = "123456"

SHA-256("123456") = 8D969EEF6ECAD3C29A3A629280E686CF
                    0C3F5D5A86AFF3CA12020C923ADC6C92

AES Key (16 bytes) = 8D 96 9E EF 6E CA D3 C2 
                     9A 3A 62 92 80 E6 86 CF
```

#### 4.3.2 Cấu Trúc Block Mã Hóa (48 bytes)

```
┌─────────────────────────────────────────────────────────────────┐
│                 48-BYTE ENCRYPTED BLOCK                         │
│              (AES-128/ECB/NoPadding = 3 blocks)                │
├──────┬──────┬──────┬───────┬───────┬─────────┬──────────┬──────┤
│ 0-3  │ 4-5  │  6   │   7   │  8-9  │   10    │  11-31   │32-43 │
├──────┼──────┼──────┼───────┼───────┼─────────┼──────────┼──────┤
│ BAL  │ EXP  │ DAY  │ MONTH │ YEAR  │ NAME_LEN│   NAME   │ CCCD │
│ 4B   │ 2B   │ 1B   │  1B   │  2B   │   1B    │  21B max │ 12B  │
└──────┴──────┴──────┴───────┴───────┴─────────┴──────────┴──────┘
│◄────────────────── Block 1 (16B) ──────────────►│
                     │◄────────────── Block 2 (16B) ──────────────►│
                                      │◄────────── Block 3 (16B) ──►│

BAL     = Balance (Big Endian, 4 bytes)
EXP     = Expiry Days (Big Endian, 2 bytes)
DAY     = Ngày sinh (1 byte)
MONTH   = Tháng sinh (1 byte)
YEAR    = Năm sinh (Big Endian, 2 bytes)
NAME_LEN = Độ dài tên (1 byte, max 21)
NAME    = Họ tên UTF-8 (max 21 bytes)
CCCD    = Căn cước công dân ASCII (12 bytes)
[44-47] = Padding zeros (4 bytes)
```

#### 4.3.3 Quy Trình Mã Hóa (Host → Card)

```java
// Phía Host: Mã hóa trước khi ghi
public static byte[] buildCardData(int userId, int balance, short expiry, 
                                   String pin, ...) throws Exception {
    byte[] cardData = new byte[80];
    
    // [0-1] UserID (không mã hóa)
    cardData[0] = (byte) ((userId >> 8) & 0xFF);
    cardData[1] = (byte) (userId & 0xFF);
    
    // Build 48-byte plaintext
    byte[] payload = new byte[48];
    // Balance (4 bytes, Big Endian)
    payload[0] = (byte) ((balance >> 24) & 0xFF);
    payload[1] = (byte) ((balance >> 16) & 0xFF);
    payload[2] = (byte) ((balance >> 8) & 0xFF);
    payload[3] = (byte) (balance & 0xFF);
    // ... các trường khác ...
    
    // Mã hóa với AES
    byte[] encrypted = encryptSensitivePayload(payload, pin);
    System.arraycopy(encrypted, 0, cardData, 2, 48);
    
    // [50] PIN Retry
    cardData[50] = pinRetry;
    
    // [51-66] PIN Hash
    byte[] pinHash = hashPIN(pin);
    System.arraycopy(pinHash, 0, cardData, 51, 16);
    
    return cardData;
}

// AES Encryption
public static byte[] encryptSensitivePayload(byte[] payload48, String pin) 
        throws Exception {
    SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
    Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, aesKey);
    return cipher.doFinal(payload48);
}
```

#### 4.3.4 Quy Trình Giải Mã (Card → Host)

```java
// Phía Card: Giải mã sau khi verify PIN
private void handleVerifyPIN(APDU apdu) {
    // ... verify PIN thành công ...
    
    // Derive key từ PIN trong APDU
    deriveAESKeyFromPIN(buf, ISO7816.OFFSET_CDATA, (short) 6);
    
    // Giải mã block [2-49]
    aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
    aesCipher.doFinal(cardData, OFFSET_BALANCE, ENC_BLOCK_LEN, 
                      tempBuffer, (short) 0);
    
    // Copy dữ liệu đã giải mã vào response buffer
    Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);
    Util.arrayCopyNonAtomic(tempBuffer, (short) 0, buf, OFFSET_BALANCE, 
                            ENC_BLOCK_LEN);
    
    apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
}
```

### 4.4 Xác Thực RSA-1024 (Chi Tiết)

#### 4.4.1 Sinh Keypair Trên Thẻ

```java
// Phía Card: Sinh RSA keypair khi tạo thẻ
private void initCrypto() {
    // Tạo RSA keypair 1024-bit
    rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
    rsaKeyPair.genKeyPair();
    
    // Khởi tạo signature engine
    rsaSignature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
    rsaSignature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
}

// Khi ghi thẻ mới (UserID = 0 → có dữ liệu), sinh keypair mới
private void handleWrite(APDU apdu) {
    boolean isBlankCard = (cardData[OFFSET_USER_ID] == 0) && 
                          (cardData[OFFSET_USER_ID + 1] == 0);
    
    if (isBlankCard || isResetting) {
        rsaKeyPair.genKeyPair();  // ✅ Sinh keypair mới
        rsaSignature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
    }
    // ... ghi dữ liệu ...
}
```

#### 4.4.2 Export Public Key

```java
// Phía Card: Trả về modulus (128 bytes) + exponent (3 bytes)
private void handleGetPublicKey(APDU apdu) {
    RSAPublicKey pubKey = (RSAPublicKey) rsaKeyPair.getPublic();
    
    byte[] buf = apdu.getBuffer();
    short offset = 0;
    
    // Copy modulus (128 bytes)
    short modulusLen = pubKey.getModulus(buf, offset);
    offset += modulusLen;
    
    // Copy exponent (3 bytes, thường là 65537 = 0x010001)
    short exponentLen = pubKey.getExponent(buf, offset);
    offset += exponentLen;
    
    apdu.setOutgoingAndSend((short) 0, offset);  // 131 bytes
}
```

#### 4.4.3 Challenge-Response Authentication

```
         HOST                                    CARD
           │                                       │
           │  1. GET_PUBLIC_KEY                    │
           │──────────────────────────────────────►│
           │                                       │
           │  [Modulus 128B][Exp 3B] + 9000        │
           │◄──────────────────────────────────────│
           │                                       │
           │  (Host lưu public key vào DB)         │
           │                                       │
           │  2. SIGN_CHALLENGE [32B random]       │
           │──────────────────────────────────────►│
           │                                       │
           │                              ┌────────┴────────┐
           │                              │ Sign với Private│
           │                              │ Key (SHA1+RSA)  │
           │                              └────────┬────────┘
           │                                       │
           │  [128B Signature] + 9000              │
           │◄──────────────────────────────────────│
           │                                       │
           │  (Host verify signature với           │
           │   Public Key đã lưu)                  │
           │                                       │
           │  ✅ Match → Thẻ thật                  │
           │  ❌ Không match → Thẻ giả             │
```

```java
// Phía Host: Verify signature
public static boolean verifySignature(byte[] challenge, byte[] signature, 
                                      PublicKey publicKey) throws Exception {
    Signature sig = Signature.getInstance("SHA1withRSA");
    sig.initVerify(publicKey);
    sig.update(challenge);
    return sig.verify(signature);
}

// Phía Card: Ký challenge
private void handleSignChallenge(APDU apdu) {
    byte[] buf = apdu.getBuffer();
    short lc = apdu.setIncomingAndReceive();
    
    if (lc != 32) {  // Challenge phải là 32 bytes
        ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }
    
    // Ký với private key
    rsaSignature.sign(buf, ISO7816.OFFSET_CDATA, lc, buf, (short) 0);
    apdu.setOutgoingAndSend((short) 0, (short) 128);
}
```

### 4.5 Đổi PIN (Re-encryption)

Khi đổi PIN, dữ liệu phải được giải mã bằng PIN cũ và mã hóa lại bằng PIN mới:

```java
private void handleChangePIN(APDU apdu) {
    // Old PIN: buf[5-10], New PIN: buf[11-16]
    short oldPinOffset = ISO7816.OFFSET_CDATA;
    short newPinOffset = (short)(ISO7816.OFFSET_CDATA + 6);
    
    // 1. Verify old PIN
    hashPIN(buf, oldPinOffset, (short) 6, tempBuffer, (short) 0);
    // ... compare with stored hash ...
    
    // 2. Decrypt with OLD PIN
    // tempBuffer[0..31]: SHA-256 output (key derivation)
    // tempBuffer[32..79]: Safe zone for decrypted data
    deriveAESKeyFromPIN(buf, oldPinOffset, (short) 6);
    aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
    aesCipher.doFinal(cardData, OFFSET_BALANCE, ENC_BLOCK_LEN, 
                      tempBuffer, (short) 32);  // ✅ Decrypt to offset 32

    // 3. Encrypt with NEW PIN
    deriveAESKeyFromPIN(buf, newPinOffset, (short) 6);
    aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
    aesCipher.doFinal(tempBuffer, (short) 32, ENC_BLOCK_LEN, 
                      cardData, OFFSET_BALANCE);
    
    // 4. Update PIN hash
    hashPIN(buf, newPinOffset, (short) 6, tempBuffer, (short) 0);
    Util.arrayCopyNonAtomic(tempBuffer, (short) 0, cardData, 
                            OFFSET_PIN_HASH, PIN_HASH_SIZE);
}
```

---

## 5. CẤU TRÚC DỮ LIỆU

### 5.1 Card Data Layout (80 bytes)

```
┌────────────────────────────────────────────────────────────────────┐
│                    CARD DATA LAYOUT (80 BYTES)                     │
├────────┬───────────────────────────────────────────────────────────┤
│ Offset │                      Description                          │
├────────┼───────────────────────────────────────────────────────────┤
│  0-1   │ UserID (2 bytes, Big Endian) - KHÔNG MÃ HÓA              │
├────────┼───────────────────────────────────────────────────────────┤
│  2-49  │ Encrypted Block (48 bytes, AES-128/ECB)                  │
│        │ ┌──────────────────────────────────────────────────────┐ │
│        │ │ [0-3]   Balance (4B)                                 │ │
│        │ │ [4-5]   ExpiryDays (2B)                              │ │
│        │ │ [6]     DOB Day (1B)                                 │ │
│        │ │ [7]     DOB Month (1B)                               │ │
│        │ │ [8-9]   DOB Year (2B)                                │ │
│        │ │ [10]    NameLen (1B)                                 │ │
│        │ │ [11-31] FullName UTF-8 (max 21B)                     │ │
│        │ │ [32-43] CCCD ASCII (12B)                             │ │
│        │ │ [44-47] Padding zeros (4B)                           │ │
│        │ └──────────────────────────────────────────────────────┘ │
├────────┼───────────────────────────────────────────────────────────┤
│   50   │ PIN Retry Counter (1 byte) - KHÔNG MÃ HÓA               │
├────────┼───────────────────────────────────────────────────────────┤
│ 51-66  │ PIN Hash (16 bytes, SHA-256 truncated) - KHÔNG MÃ HÓA   │
├────────┼───────────────────────────────────────────────────────────┤
│ 67-79  │ Reserved (13 bytes zeros)                                │
└────────┴───────────────────────────────────────────────────────────┘
```

### 5.2 CardData Model (Java)

```java
public class CardData {
    // Identification
    public int userId;           // [0-1]
    public String fullName;      // Trong encrypted block
    public String cccd;          // Trong encrypted block
    
    // Encrypted data
    public int balance;          // [2-5] trong encrypted block
    public short expiryDays;     // [6-7] trong encrypted block
    
    // Personal info (trong encrypted block)
    public byte dobDay;          // Ngày sinh
    public byte dobMonth;        // Tháng sinh
    public short dobYear;        // Năm sinh
    
    // Security
    public String pin;           // 6 chữ số, không lưu trên thẻ dạng plain
    public byte pinRetry;        // [50] số lần thử còn lại
    public byte[] pinHash;       // [51-66] SHA-256(PIN)[:16]
}
```

### 5.3 MemberRecord Model (Database)

```java
public class MemberRecord {
    public int id;                    // PRIMARY KEY = UserID
    public String fullName;           // Họ tên
    public int balanceVnd;            // Số dư (VND)
    public LocalDate birthdate;       // Ngày sinh
    public LocalDate expiryDate;      // Ngày hết hạn
    public String cardUid;            // UID vật lý thẻ
    public String rsaModulusHex;      // RSA modulus (hex)
    public String rsaExponentHex;     // RSA exponent (hex)
    public short pinretry;            // Số lần thử PIN
    public String cccd;               // CCCD
    public byte[] avatarData;         // Ảnh đại diện
    public String lastCheckinDate;    // Ngày check-in cuối
    public String createdAt;          // Thời gian tạo
    public String updatedAt;          // Cập nhật cuối
}
```

---

## 6. CÁC CHỨC NĂNG CHÍNH

### 6.1 Admin Functions

| Chức Năng | Mô Tả |
|-----------|-------|
| **Tạo Thẻ Mới** | Tạo UserID, sinh PIN, mã hóa, ghi thẻ, lưu DB |
| **Quẹt Thẻ** | Đọc thẻ, verify PIN, hiển thị thông tin |
| **Xóa Thẻ** | Reset thẻ về trạng thái trống |
| **Mở Khóa** | Reset retry counter khi thẻ bị khóa |
| **Reset PIN** | Đặt PIN mới không cần PIN cũ |
| **Sửa Thông Tin** | Cập nhật dữ liệu trên thẻ và DB |
| **Xem Thành Viên** | Danh sách tất cả thành viên từ DB |

### 6.2 Customer Functions

| Chức Năng | Mô Tả |
|-----------|-------|
| **Check-in** | Quẹt thẻ, nhập PIN, trừ ngày tập |
| **Mua Hàng** | Chọn sản phẩm, thanh toán bằng số dư thẻ |
| **Nạp Tiền** | Nạp thêm số dư vào thẻ |
| **Gia Hạn** | Mua thêm ngày tập |
| **Đổi PIN** | Thay đổi mã PIN (cần PIN cũ) |
| **Xem Lịch Sử** | Xem lịch sử giao dịch |

---

## 📚 TỔNG KẾT

### Điểm Mạnh Của Hệ Thống

1. **Bảo mật 3 lớp**: PIN + AES + RSA
2. **Dữ liệu nhạy cảm được mã hóa**: Balance, Name, DOB, CCCD
3. **Private key không rời thẻ**: RSA authentication an toàn
4. **Giới hạn thử PIN**: Chống brute-force
5. **Đồng bộ thẻ-DB**: Backup dữ liệu, xác thực chéo

### Các Thuật Toán Sử Dụng

| Mục Đích | Thuật Toán | Chi Tiết |
|----------|------------|----------|
| PIN Hash | SHA-256 | Truncate 16 bytes |
| Data Encryption | AES-128/ECB | Key từ SHA-256(PIN) |
| Card Authentication | RSA-1024 | SHA1withRSA PKCS#1 |

### Files Quan Trọng

```
SmartCard.java      - Java Card Applet (chạy trên thẻ)
PcscClient.java     - PC/SC connection handler
CardHelper.java     - APDU builder/parser
CryptoHelper.java   - Crypto utilities (Host-side)
MembersDao.java     - Database access
FunctionPanel.java  - Admin UI
CustomerWindow.java - Customer UI
```

---

*Tài liệu được tạo tự động từ source code - Phiên bản 2.0*
