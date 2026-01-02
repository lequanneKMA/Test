import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple SQLite DAO for members.db
 * Requires sqlite-jdbc on classpath: org.sqlite.JDBC
 */
public class MembersDao {
    private final String dbUrl;

    public MembersDao() {
        this("jdbc:sqlite:members.db");
    }

    public MembersDao(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    private Connection getConnection() throws SQLException {
        try {
            // Attempt to load driver (ok if using JDBC 4 but helps older setups)
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Driver not found: connection may still work with modern JDBC, else SQL exception will be thrown.
        }
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS members (\n" +
                        "  id INTEGER PRIMARY KEY,\n" +
                    "  full_name TEXT,\n" +
                    "  balance_vnd INTEGER DEFAULT 0,\n" +
                    "  birthdate TEXT,\n" +
                    "  expiry_date TEXT,\n" +
                    "  card_uid TEXT,\n" +
                    "  rsa_public_key TEXT,\n" +
                    "  transaction_history TEXT,\n" +
                    "  pinretry INTEGER DEFAULT 5,\n" +
                        "  cccd TEXT,\n" +
                        "  avatar_data BLOB,\n" +
                    "  created_at TEXT DEFAULT CURRENT_TIMESTAMP,\n" +
                    "  updated_at TEXT DEFAULT CURRENT_TIMESTAMP\n" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_card_uid ON members(card_uid)");
                // Attempt to add columns if older DB exists (ignore errors if already present)
                try { st.executeUpdate("ALTER TABLE members ADD COLUMN cccd TEXT"); } catch (Exception ignored) {}
                try { st.executeUpdate("ALTER TABLE members ADD COLUMN avatar_data BLOB"); } catch (Exception ignored) {}
        }
        return conn;
    }

    public MemberRecord getByUserId(int userId) throws SQLException {
        String sql = "SELECT id, full_name, balance_vnd, birthdate, expiry_date, card_uid, rsa_public_key, transaction_history, pinretry, cccd, avatar_data, created_at, updated_at " +
                     "FROM members WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        }
    }

    public List<MemberRecord> getAll() throws SQLException {
        String sql = "SELECT id, full_name, balance_vnd, birthdate, expiry_date, card_uid, rsa_public_key, transaction_history, pinretry, cccd, avatar_data, created_at, updated_at FROM members ORDER BY id";
        List<MemberRecord> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void upsert(MemberRecord m) throws SQLException {
        String sql = "INSERT INTO members (id, full_name, balance_vnd, birthdate, expiry_date, card_uid, rsa_public_key, transaction_history, pinretry, cccd, avatar_data, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "full_name=excluded.full_name, balance_vnd=excluded.balance_vnd, birthdate=excluded.birthdate, " +
                     "expiry_date=excluded.expiry_date, card_uid=excluded.card_uid, rsa_public_key=excluded.rsa_public_key, " +
                     "transaction_history=excluded.transaction_history, pinretry=excluded.pinretry, cccd=excluded.cccd, avatar_data=excluded.avatar_data, updated_at=CURRENT_TIMESTAMP";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.id);
            ps.setString(2, m.fullName);
            ps.setInt(3, m.balanceVnd);
            ps.setString(4, m.birthdate != null ? m.birthdate.toString() : null);
            ps.setString(5, m.expiryDate != null ? m.expiryDate.toString() : null);
            ps.setString(6, m.cardUid);
            ps.setString(7, m.rsaPublicKey);
            ps.setString(8, m.transactionHistory);
            ps.setShort(9, m.pinretry);
            ps.setString(10, m.cccd);
            ps.setBytes(11, m.avatarData);
            ps.executeUpdate();
        }
    }

    public void updateBalanceAndExpiry(int memberId, int newBalance, LocalDate newExpiryDate) throws SQLException {
        String sql = "UPDATE members SET balance_vnd = ?, expiry_date = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newBalance);
            ps.setString(2, newExpiryDate != null ? newExpiryDate.toString() : null);
            ps.setInt(3, memberId);
            ps.executeUpdate();
        }
    }

    public void updatePinRetry(int memberId, short retries) throws SQLException {
        String sql = "UPDATE members SET pinretry = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setShort(1, retries);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        }
    }

    private MemberRecord map(ResultSet rs) throws SQLException {
        MemberRecord m = new MemberRecord();
        m.id = rs.getInt("id");
        m.fullName = rs.getString("full_name");
        m.balanceVnd = rs.getInt("balance_vnd");
        m.cardUid = rs.getString("card_uid");
        m.rsaPublicKey = rs.getString("rsa_public_key");
        m.transactionHistory = rs.getString("transaction_history");
        m.pinretry = rs.getShort("pinretry");
        m.createdAt = rs.getString("created_at");
        m.updatedAt = rs.getString("updated_at");
            m.cccd = rs.getString("cccd");
            try { m.avatarData = rs.getBytes("avatar_data"); } catch (SQLException ignored) { m.avatarData = null; }
        // Parse dates if present
        String bd = rs.getString("birthdate");
        m.birthdate = (bd != null && !bd.isEmpty()) ? LocalDate.parse(bd) : null;
        String ed = rs.getString("expiry_date");
        m.expiryDate = (ed != null && !ed.isEmpty()) ? LocalDate.parse(ed) : null;
        return m;
    }
}
