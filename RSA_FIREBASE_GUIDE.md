# RSA Card Authentication - Firebase Integration

## 📖 Giải thích RSA trong hệ thống

### 🎯 Mục đích:
RSA-1024 được dùng để **chống giả mạo thẻ** (card authentication). 

**Vấn đề cần giải quyết:**
- Ai đó có thể tạo thẻ giả với cùng UserID
- Kẻ gian có thể clone dữ liệu từ thẻ thật sang thẻ rỗng
- Cần cách để **chứng minh thẻ là thật**

**Giải pháp RSA:**
- Mỗi thẻ có RSA key pair (Public Key + Private Key)
- **Private Key**: Lưu trên thẻ, KHÔNG BAO GIỜ rời khỏi card
- **Public Key**: Export ra và lưu trên Firebase server
- Chỉ thẻ thật mới có Private Key đúng để ký challenge

---

## 🔐 Challenge-Response Protocol

### Flow hoạt động:

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│  Client  │         │   Card   │         │ Firebase │
│   App    │         │ (Applet) │         │  Server  │
└────┬─────┘         └────┬─────┘         └────┬─────┘
     │                    │                     │
     │ 1. Request Challenge                    │
     │────────────────────────────────────────>│
     │                    │                     │
     │              2. Generate Random 32 bytes│
     │                    │                     │
     │<────────────────────────────────────────│
     │   challenge (base64)                    │
     │                    │                     │
     │ 3. SIGN CHALLENGE  │                     │
     │   (0x88 + 32 bytes)│                     │
     │───────────────────>│                     │
     │                    │                     │
     │                    │ 4. RSA Sign with    │
     │                    │    Private Key      │
     │                    │                     │
     │ 5. signature       │                     │
     │   (128 bytes)      │                     │
     │<───────────────────│                     │
     │                    │                     │
     │ 6. Verify Signature                     │
     │    userId + signature (base64)          │
     │────────────────────────────────────────>│
     │                    │                     │
     │                    │ 7. Get Public Key   │
     │                    │    from database    │
     │                    │                     │
     │                    │ 8. RSA Verify       │
     │                    │                     │
     │<────────────────────────────────────────│
     │   {verified: true/false, cardData: {...}}
     │                    │                     │
```

### Tại sao an toàn?

1. **Private Key không rời khỏi thẻ**
   - Kẻ gian không thể đọc Private Key từ thẻ
   - Chỉ có thể yêu cầu thẻ ký (sign) data

2. **Challenge là random mỗi lần**
   - Không thể replay attack (dùng lại signature cũ)
   - Mỗi lần quẹt thẻ = challenge mới

3. **Signature chỉ đúng với Public Key tương ứng**
   - Thẻ giả không có Private Key đúng
   - Signature sai → Server từ chối

---

## 🔥 Setup Firebase

### 1. Cài đặt Firebase Functions:

```bash
# Vào thư mục firebase
cd firebase

# Init Firebase (nếu chưa có)
firebase init functions

# Cài dependencies
cd functions
npm install firebase-admin firebase-functions
```

### 2. Deploy functions:

```bash
firebase deploy --only functions
```

### 3. Lấy URL của functions:

Sau khi deploy, Firebase sẽ show URL:
```
✔  functions[generateChallenge]: https://YOUR-PROJECT.cloudfunctions.net/generateChallenge
✔  functions[verifyCardSignature]: https://YOUR-PROJECT.cloudfunctions.net/verifyCardSignature
✔  functions[registerCard]: https://YOUR-PROJECT.cloudfunctions.net/registerCard
```

### 4. Cập nhật URL trong Java code:

Mở `FirebaseCardAuth.java`, sửa dòng:
```java
private static final String FIREBASE_FUNCTION_URL = "https://YOUR-PROJECT.cloudfunctions.net";
```

---

## 💻 Sử dụng trong Code

### A. Khi Admin TẠO THẺ MỚI:

```java
// Trong FunctionPanel.createNewCard()

// 1. Ghi data lên thẻ (như cũ)
CommandAPDU writeCmd = CardHelper.buildWriteCommand(newCard);
ResponseAPDU writeResp = pcsc.transmit(writeCmd);

if ((writeResp.getSW() & 0xFF00) == 0x9000) {
    // 2. Register card với Firebase (upload public key)
    try {
        boolean registered = FirebaseCardAuth.registerCard(
            pcsc, 
            newCard.userId, 
            newCard.fullName, 
            newCard.getDobString(),
            newCard.balance, 
            newCard.expiryDays
        );
        
        if (registered) {
            logArea.append("✅ Card registered with Firebase!\n");
            logArea.append("🔑 RSA Public Key uploaded\n");
        } else {
            logArea.append("⚠️ Firebase registration failed\n");
        }
    } catch (Exception e) {
        logArea.append("❌ Firebase error: " + e.getMessage() + "\n");
    }
}
```

### B. Khi Customer QUẸT THẺ:

**Option 1: Với Firebase (Full Security)**

```java
// Trong FunctionPanel.readCustomerCard() hoặc CustomerWindow

try {
    // 1. Authenticate card với challenge-response
    CardData card = FirebaseCardAuth.authenticateCard(pcsc, userId);
    
    if (card != null) {
        // ✅ Thẻ là THẬT
        logArea.append("✅ CARD AUTHENTIC\n");
        logArea.append("👤 Họ Tên: " + card.fullName + "\n");
        logArea.append("💰 Số Dư: " + card.balance + "\n");
        // ... show data
    } else {
        // ❌ Thẻ là GIẢ
        logArea.append("❌ FAKE CARD DETECTED!\n");
        logArea.append("⚠️ Security alert - contact admin\n");
        JOptionPane.showMessageDialog(this, 
            "THẺ GIẢ!\nVui lòng liên hệ quản lý.",
            "Security Alert", 
            JOptionPane.ERROR_MESSAGE);
    }
} catch (Exception e) {
    logArea.append("❌ Authentication error: " + e.getMessage() + "\n");
}
```

**Option 2: Local Verification (Offline Mode)**

```java
// Không cần Firebase - verify local
try {
    boolean authentic = FirebaseCardAuth.authenticateCardLocal(pcsc);
    
    if (authentic) {
        logArea.append("✅ Card signature valid\n");
        // Proceed with reading data
    } else {
        logArea.append("❌ Invalid card signature\n");
    }
} catch (Exception e) {
    logArea.append("❌ Verification error: " + e.getMessage() + "\n");
}
```

---

## 🎓 Khi nào nên dùng RSA?

### ✅ NÊN dùng RSA khi:
- Hệ thống có giá trị cao (ngân hàng, bảo mật cao)
- Lo ngại về thẻ giả mạo
- Cần audit trail (log mọi transaction)
- Có server backend (Firebase/AWS/Azure)

### ❌ KHÔNG CẦN RSA khi:
- App đơn giản, offline
- Gym nhỏ, ít khách
- Budget hạn chế (RSA phức tạp)
- AES + SHA-256 đã đủ an toàn

---

## 📊 So sánh bảo mật:

| Tính năng | Không mã hóa | AES + SHA-256 | + RSA-1024 |
|-----------|--------------|---------------|------------|
| PIN bảo vệ | ❌ | ✅ | ✅ |
| Balance mã hóa | ❌ | ✅ | ✅ |
| PIN hash | ❌ | ✅ | ✅ |
| Chống clone thẻ | ❌ | ⚠️ Một phần | ✅ Hoàn toàn |
| Cần server | ❌ | ❌ | ✅ |
| Độ phức tạp | Thấp | Trung bình | Cao |

---

## ⚙️ Config cho dự án của bạn:

### 1. Thêm dependency JSON:

Download: https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar

Copy vào: `lib/json-20231013.jar`

Compile:
```bash
javac -encoding UTF-8 -cp "src;lib/json-20231013.jar" -d build/classes src/*.java
```

Run:
```bash
java -cp "build/classes;lib/json-20231013.jar" GymAppLauncher
```

### 2. Hoặc dùng Maven/Gradle:

**Maven (pom.xml):**
```xml
<dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20231013</version>
</dependency>
```

**Gradle (build.gradle):**
```gradle
implementation 'org.json:json:20231013'
```

---

## 🧪 Testing Flow:

### Test 1: Register card
```bash
1. Admin → Tạo Thẻ Mới
2. Check log: "🔑 RSA Public Key uploaded"
3. Check Firebase Console: /cards/{userId}/publicKey có data
```

### Test 2: Authenticate card (Firebase)
```bash
1. Customer → Quẹt Thẻ
2. App request challenge từ Firebase
3. Card ký challenge → signature
4. Firebase verify → "✅ CARD AUTHENTIC"
```

### Test 3: Detect fake card
```bash
1. Tạo thẻ mới với cùng UserID nhưng khác Private Key
2. Quẹt thẻ giả
3. Signature sai → "❌ FAKE CARD DETECTED"
```

---

## 🚀 Kết luận:

Bạn có **2 lựa chọn**:

### ⚡ SIMPLE MODE (Khuyên dùng cho gym nhỏ):
- Chỉ dùng AES-128 + SHA-256
- Không cần Firebase
- Đơn giản, offline hoàn toàn
- Đủ an toàn cho gym app

### 🔒 FULL SECURITY MODE (Nếu cần maximum security):
- AES + SHA-256 + RSA-1024
- Cần Firebase backend
- Chống clone/fake card 100%
- Phức tạp hơn nhưng bank-level security

**Bạn muốn dùng mode nào?** 😊
