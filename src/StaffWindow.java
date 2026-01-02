import javax.swing.*;
import java.awt.*;

/**
 * Cửa sổ Admin/Nhân Viên - 2 panel: trái chọn role, phải hiển thị chức năng
 */
public class StaffWindow extends JFrame implements RoleSelectionPanel.RoleListener {
    private final RoleSelectionPanel rolePanel;
    private final FunctionPanel functionPanel;
    private final PcscClient pcsc;

    public StaffWindow(PcscClient pcsc) {
        this.pcsc = pcsc;
        setTitle("ADMIN/NHÂN VIÊN - Quản Lý Thẻ GYM");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLocation(800, 100); 

        // Add modern title bar
        JPanel titlePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(57, 73, 171), 
                                                           getWidth(), 0, new Color(103, 58, 183));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        titlePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 12));
        JLabel titleLabel = new JLabel("ADMIN/NHÂN VIÊN - Quản Lý Thẻ GYM");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        titlePanel.add(titleLabel);
        titlePanel.setPreferredSize(new Dimension(1100, 55));
        add(titlePanel, BorderLayout.NORTH);

        // Tạo 2 panel
        rolePanel = new RoleSelectionPanel(this);
        functionPanel = new FunctionPanel(pcsc);

        // SplitPane: trái 280px, phải còn lại
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rolePanel, functionPanel);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(4);
        splitPane.setOneTouchExpandable(false);
        splitPane.setBackground(new Color(248, 249, 250));

        add(splitPane, BorderLayout.CENTER);
        setVisible(true);
    }

    @Override
    public void onRoleSelected(String role) {
        functionPanel.switchRole(role);
    }

    public static void main(String[] args) {
        try {
            PcscClient pcsc = new PcscClient();
            SwingUtilities.invokeLater(() -> new StaffWindow(pcsc));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Lỗi: " + e.getMessage());
        }
    }
}
