import javax.swing.*;
import java.awt.*;

/**
 * Panel bên trái - Chọn role (Nhân Viên / Admin)
 */
public class RoleSelectionPanel extends JPanel {
    public interface RoleListener {
        void onRoleSelected(String role);
    }

    private final RoleListener listener;
    private JButton employeeBtn;
    private JButton adminBtn;

    public RoleSelectionPanel(RoleListener listener) {
        this.listener = listener;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(240, 242, 247));
        setBorder(BorderFactory.createEmptyBorder(25, 15, 25, 15));

        // Title with modern styling
        JLabel titleLabel = new JLabel("Lựa chọn vai trò");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(57, 73, 171));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(35));

        // Employee Button - Modern styling
        employeeBtn = new JButton("NHÂN VIÊN");
        employeeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        employeeBtn.setMaximumSize(new Dimension(210, 55));
        employeeBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        employeeBtn.setBackground(new Color(76, 175, 80));
        employeeBtn.setForeground(Color.WHITE);
        employeeBtn.setFocusPainted(false);
        employeeBtn.setBorder(BorderFactory.createRaisedBevelBorder());
        employeeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        employeeBtn.addActionListener(e -> {
            employeeBtn.setBackground(new Color(60, 150, 65));
            adminBtn.setBackground(new Color(156, 39, 176));
            if (listener != null) listener.onRoleSelected("EMPLOYEE");
        });
        add(employeeBtn);
        add(Box.createVerticalStrut(25));

        // Admin Button - Modern styling
        adminBtn = new JButton("ADMIN");
        adminBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        adminBtn.setMaximumSize(new Dimension(210, 55));
        adminBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        adminBtn.setBackground(new Color(156, 39, 176));
        adminBtn.setForeground(Color.WHITE);
        adminBtn.setFocusPainted(false);
        adminBtn.setBorder(BorderFactory.createRaisedBevelBorder());
        adminBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        adminBtn.addActionListener(e -> {
            adminBtn.setBackground(new Color(130, 20, 150));
            employeeBtn.setBackground(new Color(76, 175, 80));
            // Prompt for admin password before allowing access
            JPasswordField pwdField = new JPasswordField(12);
            JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
            panel.add(new JLabel("Nhập mật khẩu Admin:"));
            panel.add(pwdField);
            int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Xác Thực Admin",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (option == JOptionPane.OK_OPTION) {
                String pwd = new String(pwdField.getPassword());
                if ("admin123@".equals(pwd)) {
                    if (listener != null) listener.onRoleSelected("ADMIN");
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "❌ Mật khẩu Admin không đúng!",
                        "Từ Chối",
                        JOptionPane.ERROR_MESSAGE
                    );
                    // Keep current role as employee
                    if (listener != null) listener.onRoleSelected("EMPLOYEE");
                }
            }
        });
        add(adminBtn);

        add(Box.createVerticalGlue());

        // Mặc định chọn Employee
        employeeBtn.setBackground(new Color(80, 160, 120));
    }
}
