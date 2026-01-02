import javax.swing.*;
import java.awt.*;

/**
 * Launcher - Mở cả 2 cửa sổ (Khách Hàng + Admin/Nhân Viên) đồng thời
 */
public class GymAppLauncher extends JFrame {
    public GymAppLauncher() {
        setTitle("HỆ THỐNG ĐẠI HỘI GYM - KHỞI ĐỘNG");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(null);

        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(240, 248, 255));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel titleLabel = new JLabel("Đang khởi động hệ thống...");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(titleLabel, gbc);

        // Status
        JLabel statusLabel = new JLabel("Vui lòng chờ...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        gbc.gridy = 1;
        add(statusLabel, gbc);

        setVisible(true);

        // Launch both windows in background
        new Thread(() -> {
            try {
                // Initialize PC/SC
                PcscClient pcsc = new PcscClient();
                
                // Open Customer Window
                SwingUtilities.invokeLater(() -> {
                    try {
                        new CustomerWindow(pcsc);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Lỗi Khách Hàng: " + ex.getMessage());
                    }
                });

                // Wait a bit
                Thread.sleep(300);

                // Open Staff Window
                SwingUtilities.invokeLater(() -> {
                    try {
                        new StaffWindow(pcsc);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Lỗi Admin/Nhân Viên: " + ex.getMessage());
                    }
                });

                // Close launcher
                Thread.sleep(1000);
                SwingUtilities.invokeLater(() -> dispose());

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khởi động: " + ex.getMessage());
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GymAppLauncher());
    }
}
