/**
 * Card Data Model - FIXED VERSION
 * 
 * Structure (64 bytes) - FIXED Layout:
 * [0-1]   UserID (2 bytes) - Public
 * [2-17]  Encrypted Block (16 bytes) - Balance + Expiry encrypted with AES-128
 * [18-33] PIN Hash (16 bytes) - SHA-256 truncated to 16 bytes
 * [34]    PIN Retry Counter (1 byte)
 * [35]    DOB Day (1 byte) - Public
 * [36]    DOB Month (1 byte) - Public
 * [37-38] DOB Year (2 bytes) - Public
 * [39-63] FullName (25 bytes) - Public (UTF-8)
 */
public class CardData {
    // Card identification
    public int userId;
    public String fullName;
    public String cccd;
    
    // Encrypted data
    public int balance;
    public short expiryDays;
    
    // Security
    public String pin;
    public byte pinRetry;
    public byte[] pinHash;
    
    // Personal info
    public byte dobDay;
    public byte dobMonth;
    public short dobYear;
    
    public static final byte MAX_PIN_RETRY = 5;

    public CardData() {
        this.pinRetry = MAX_PIN_RETRY;
        this.fullName = "";
        this.cccd = "";
    }

    public CardData(int userId, int balance, short expiryDays, String pin, byte pinRetry, 
                   byte dobDay, byte dobMonth, short dobYear) {
        this.userId = userId;
        this.balance = balance;
        this.expiryDays = expiryDays;
        this.pin = pin;
        this.pinRetry = pinRetry;
        this.dobDay = dobDay;
        this.dobMonth = dobMonth;
        this.dobYear = dobYear;
        this.fullName = "";
        this.cccd = "";
    }
    
    public CardData(int userId, String fullName, int balance, short expiryDays, 
                   String pin, byte pinRetry, byte dobDay, byte dobMonth, short dobYear) {
        this(userId, balance, expiryDays, pin, pinRetry, dobDay, dobMonth, dobYear);
        this.fullName = fullName != null ? fullName : "";
        this.cccd = "";
    }

    public String getDobString() {
        if (dobDay == 0 || dobMonth == 0 || dobYear == 0) {
            return "Chưa cập nhật";
        }
        return String.format("%02d/%02d/%04d", dobDay, dobMonth, dobYear);
    }

    public boolean isLocked() {
        return pinRetry == 0;
    }
    
    public boolean isEncrypted() {
        return balance == -1 && expiryDays == -1;
    }
    
    public boolean isExpired() {
        return expiryDays <= 0;
    }
    
    public boolean isExpiringSoon() {
        return expiryDays > 0 && expiryDays <= 7;
    }
    
    public String getStatusEmoji() {
        if (isLocked()) return "🔒";
        if (isExpired()) return "❌";
        if (isExpiringSoon()) return "⚠️";
        return "✅";
    }
    
    public String getStatusText() {
        if (isLocked()) return "KHÓA";
        if (isExpired()) return "HẾT HẠN";
        if (isExpiringSoon()) return "SẮP HẾT HẠN";
        return "HOẠT ĐỘNG";
    }

    @Override
    public String toString() {
        return String.format(
            "CardData{id=%d, name='%s', dob=%s, balance=%s, expiry=%s, retry=%d/%d, status=%s}",
            userId, 
            fullName != null ? fullName : "",
            getDobString(),
            balance == -1 ? "ENCRYPTED" : String.format("%,d VND", balance),
            expiryDays == -1 ? "ENCRYPTED" : expiryDays + " days",
            pinRetry, 
            MAX_PIN_RETRY,
            getStatusText()
        );
    }
    
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("════════════════════════════\n");
        sb.append("       THÔNG TIN THẺ\n");
        sb.append("════════════════════════════\n");
        
        if (fullName != null && !fullName.isEmpty()) {
            sb.append("👤 Họ Tên: ").append(fullName).append("\n");
        }
        
        sb.append("Ngày Sinh: ").append(getDobString()).append("\n");
        sb.append("ID Thẻ: ").append(userId).append("\n");
        
        // CRITICAL: Check if encrypted before display
        if (balance == -1) {
            sb.append("Số Dư: [MÃ HÓA]\n");
            sb.append("Cần xác thực PIN để xem\n");
        } else {
            sb.append("Số Dư: ").append(String.format("%,d VND", balance)).append("\n");
        }
        
        if (expiryDays == -1) {
            sb.append("Hạn Tập: [MÃ HÓA]\n");
            sb.append("Cần xác thực PIN để xem\n");
        } else {
            sb.append("Hạn Tập: ").append(expiryDays).append(" ngày\n");
            if (isExpired()) {
                sb.append("   ❌ THẺ ĐÃ HẾT HẠN!\n");
            } else if (isExpiringSoon()) {
                sb.append("   ⚠️ THẺ SẮP HẾT HẠN!\n");
            }
        }
        
        sb.append("\n");
        
        if (isLocked()) {
            sb.append("Trạng Thái: KHÓA\n");
            sb.append("⚠️ Liên hệ admin để mở khóa\n");
        } else {
            sb.append("✅ Trạng Thái: ").append(getStatusText()).append("\n");
            sb.append("🔑 Lần Thử PIN: ").append(pinRetry).append("/").append(MAX_PIN_RETRY).append("\n");
        }
        
        sb.append("════════════════════════════\n");
        return sb.toString();
    }
    
    public CardData copy() {
        CardData copy = new CardData(
            this.userId,
            this.fullName,
            this.balance,
            this.expiryDays,
            this.pin,
            this.pinRetry,
            this.dobDay,
            this.dobMonth,
            this.dobYear
        );
        
        if (this.pinHash != null) {
            copy.pinHash = this.pinHash.clone();
        }
        
        return copy;
    }
    
    public String validate() {
        if (userId < 0 || userId > 65535) {
            return "UserID phải trong khoảng 0-65535";
        }
        
        if (balance < 0) {
            return "Số dư không thể âm";
        }
        
        if (expiryDays < 0) {
            return "Hạn tập không thể âm";
        }
        
        if (pinRetry < 0 || pinRetry > MAX_PIN_RETRY) {
            return "PIN retry phải trong khoảng 0-5";
        }
        
        if (dobDay < 1 || dobDay > 31) {
            return "Ngày sinh không hợp lệ (1-31)";
        }
        
        if (dobMonth < 1 || dobMonth > 12) {
            return "Tháng sinh không hợp lệ (1-12)";
        }
        
        if (dobYear < 1900 || dobYear > 2099) {
            return "Năm sinh không hợp lệ (1900-2099)";
        }
        
        if (fullName != null && fullName.getBytes().length > 25) {
            return "Họ tên quá dài (max 25 bytes UTF-8)";
        }
        
        return null;
    }
    
    public boolean isBlank() {
        return userId == 0 && 
               balance == 0 && 
               expiryDays == 0 &&
               (fullName == null || fullName.isEmpty());
    }
}