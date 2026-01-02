import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Dialog hiển thị toàn bộ thành viên từ Database.
 */
public class MembersTableDialog extends JDialog {
    private final MembersDao dao = new MembersDao();
    private final DefaultTableModel model;
    private final JTextField searchField;

    public MembersTableDialog(Frame owner) {
        super(owner, "Thành Viên (Database)", true);
        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new BorderLayout(8,8));
        searchField = new JTextField();
        JButton refreshBtn = new JButton("Tải Lại");
        JButton txBtn = new JButton("Lịch Sử Giao Dịch");
        refreshBtn.addActionListener(e -> reload());
        txBtn.addActionListener(e -> {
            new TransactionsTableDialog(owner).setVisible(true);
        });
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftTop.add(new JLabel("Tìm (ID/Họ Tên/CCCD):"));
        leftTop.add(txBtn);
        top.add(leftTop, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(refreshBtn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{
            "ID", "Họ Tên", "Số Dư (VND)", "Ngày Sinh", "Hạn Tập", "CCCD", "RSA", "Modulus (Hex)", "Exponent (Hex)", "Lịch Sử GD", "PIN Retry", "Tạo Lúc", "Cập Nhật"
        }, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        reload();
        // Simple filter on typing
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void apply() {
                String q = searchField.getText().trim().toLowerCase();
                reload(q);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
        });
    }

    private void reload() { reload(""); }

    private void reload(String query) {
        // Clear
        model.setRowCount(0);
        try {
            List<MemberRecord> all = dao.getAll();
            for (MemberRecord m : all) {
                if (!query.isEmpty()) {
                    String idStr = String.valueOf(m.id);
                    String name = m.fullName != null ? m.fullName.toLowerCase() : "";
                    String cccd = m.cccd != null ? m.cccd.toLowerCase() : "";
                    String modHex = m.rsaModulusHex != null ? m.rsaModulusHex.toLowerCase() : "";
                    String expHex = m.rsaExponentHex != null ? m.rsaExponentHex.toLowerCase() : "";
                    if (!(idStr.contains(query) || name.contains(query) || cccd.contains(query) || modHex.contains(query) || expHex.contains(query))) {
                        continue;
                    }
                }
                model.addRow(new Object[]{
                    m.id,
                    m.fullName,
                    m.balanceVnd,
                    m.birthdate != null ? m.birthdate : "",
                    m.expiryDate != null ? m.expiryDate : "",
                    m.cccd != null ? m.cccd : "",
                    ((m.rsaModulusHex != null && !m.rsaModulusHex.isEmpty()) && (m.rsaExponentHex != null && !m.rsaExponentHex.isEmpty())) ? "Có" : "Không",
                    m.rsaModulusHex != null ? m.rsaModulusHex : "",
                    m.rsaExponentHex != null ? m.rsaExponentHex : "",
                    m.transactionHistory != null ? m.transactionHistory : "",
                    m.pinretry,
                    m.createdAt != null ? m.createdAt : "",
                    m.updatedAt != null ? m.updatedAt : ""
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "❌ Lỗi tải dữ liệu Database: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
