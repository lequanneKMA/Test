import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Dialog hiển thị lịch sử giao dịch toàn bộ người dùng.
 */
public class TransactionsTableDialog extends JDialog {
    private final TransactionsDao dao = new TransactionsDao();
    private final DefaultTableModel model;

    public TransactionsTableDialog(Frame owner) {
        super(owner, "Lịch Sử Giao Dịch", true);
        setSize(900, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton refreshBtn = new JButton("Tải Lại");
        refreshBtn.addActionListener(e -> reload());
        top.add(refreshBtn);
        add(top, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{
            "ID", "Member ID", "Loại", "Số tiền", "Items", "Phương Thức", "Thời Gian"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(350);
        table.getColumnModel().getColumn(5).setPreferredWidth(120);
        table.getColumnModel().getColumn(6).setPreferredWidth(150);
        add(new JScrollPane(table), BorderLayout.CENTER);

        reload();
    }

    private void reload() {
        model.setRowCount(0);
        try {
            List<TransactionRecord> list = dao.getAll();
            for (TransactionRecord t : list) {
                model.addRow(new Object[]{
                    t.id,
                    t.memberId,
                    t.type,
                    t.amount,
                    t.items != null ? t.items : "",
                    t.paymentMethod != null ? t.paymentMethod : "",
                    t.createdAt
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "❌ Lỗi tải lịch sử giao dịch: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
