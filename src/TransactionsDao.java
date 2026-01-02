import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionsDao {
    private final String dbUrl;

    public TransactionsDao() {
        this("jdbc:sqlite:members.db");
    }

    public TransactionsDao(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  member_id INTEGER NOT NULL,\n" +
                    "  type TEXT NOT NULL,\n" +
                    "  amount INTEGER NOT NULL,\n" +
                    "  items TEXT,\n" +
                    "  payment_method TEXT,\n" +
                    "  created_at TEXT DEFAULT (datetime('now','localtime')),\n" +
                    "  FOREIGN KEY(member_id) REFERENCES members(id)\n" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_member ON transactions(member_id)");
        }
        return conn;
    }

    public void logTopup(int memberId, int amount, String paymentMethod) throws SQLException {
        String sql = "INSERT INTO transactions (member_id, type, amount, items, payment_method, created_at) VALUES (?,?,?,?,?,datetime('now','localtime'))";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setString(2, "TOPUP");
            ps.setInt(3, amount);
            ps.setString(4, null);
            ps.setString(5, paymentMethod);
            ps.executeUpdate();
        }
    }

    public void logPurchase(int memberId, List<CardEventBroadcaster.CartItem> items, int totalPrice) throws SQLException {
        String itemsJson = toItemsJson(items);
        String sql = "INSERT INTO transactions (member_id, type, amount, items, payment_method, created_at) VALUES (?,?,?,?,?,datetime('now','localtime'))";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setString(2, "PURCHASE");
            ps.setInt(3, totalPrice);
            ps.setString(4, itemsJson);
            ps.setString(5, null);
            ps.executeUpdate();
        }
    }

    public void logRenew(int memberId, int daysAdded, int price) throws SQLException {
        String sql = "INSERT INTO transactions (member_id, type, amount, items, payment_method, created_at) VALUES (?,?,?,?,?,datetime('now','localtime'))";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setString(2, "RENEW");
            ps.setInt(3, price);
            ps.setString(4, "{\"daysAdded\":" + daysAdded + "}");
            ps.setString(5, null);
            ps.executeUpdate();
        }
    }

    public List<TransactionRecord> getAll() throws SQLException {
        String sql = "SELECT id, member_id, type, amount, items, payment_method, created_at FROM transactions ORDER BY id DESC";
        List<TransactionRecord> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                TransactionRecord t = new TransactionRecord();
                t.id = rs.getInt("id");
                t.memberId = rs.getInt("member_id");
                t.type = rs.getString("type");
                t.amount = rs.getInt("amount");
                t.items = rs.getString("items");
                t.paymentMethod = rs.getString("payment_method");
                t.createdAt = rs.getString("created_at");
                list.add(t);
            }
        }
        return list;
    }

    private String toItemsJson(List<CardEventBroadcaster.CartItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < items.size(); i++) {
            CardEventBroadcaster.CartItem ci = items.get(i);
            sb.append("{\"id\":").append(ci.item.id)
              .append(",\"name\":\"").append(ci.item.name.replace("\"", "\\\"")).append("\"")
              .append(",\"price\":").append(ci.item.price)
              .append(",\"quantity\":").append(ci.quantity)
              .append("}");
            if (i < items.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
