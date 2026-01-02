import java.time.LocalDate;

/**
 * Member record mapped from members table.
 */
public class MemberRecord {
    public int id;
    public String fullName;
    public int balanceVnd;
    public LocalDate birthdate;
    public LocalDate expiryDate;
    public String cardUid;
    public String rsaPublicKey;
    public String rsaModulusHex;
    public String rsaExponentHex;
    public String transactionHistory;
    public short pinretry;
    public String createdAt;
    public String updatedAt;
    public String cccd;
    public byte[] avatarData;
    public String lastCheckinDate;
}
