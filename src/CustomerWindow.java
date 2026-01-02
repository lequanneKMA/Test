import javax.smartcardio.ResponseAPDU;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Cửa sổ riêng cho khách hàng - Quẹt thẻ và xem thông tin
 */
public class CustomerWindow extends JFrame {
    private final JButton swipeBtn;
    private final JTextArea infoArea;
    private final JButton personalInfoBtn;
    private final JButton renewBtn;
    private final JButton changePinBtn;
    private final JButton purchaseBtn;
    private final JButton topupBtn;
    private final JButton checkinBtn;
    private final JLabel statusLabel;
    private final PcscClient pcsc;
    private CardData currentCard;

    public CustomerWindow(PcscClient pcsc) {
        this.pcsc = pcsc;
        setTitle("KHÁCH HÀNG - Dịch Vụ Tự Phục Vụ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // Top: Title Panel 
        JPanel topPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(25, 118, 210), 
                                                           getWidth(), 0, new Color(56, 142, 226));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        topPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 15));
        JLabel titleLabel = new JLabel("♣ GYM - KHÁCH HÀNG ♣");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        topPanel.add(titleLabel);
        topPanel.setPreferredSize(new Dimension(700, 60));
        add(topPanel, BorderLayout.NORTH);

        // Center: Info display 
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        infoArea.setBackground(new Color(245, 245, 250));
        infoArea.setForeground(new Color(30, 40, 50));
        infoArea.setMargin(new Insets(10, 10, 10, 10));
        infoArea.setText("Vui lòng nhấn 'Quẹt Thẻ' để bắt đầu\n\n");
        JScrollPane scrollPane = new JScrollPane(infoArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 210), 1));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: Buttons Panel with improved layout
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(new Color(248, 250, 252));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        // Status label with icon
        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(60, 180, 60));
        bottomPanel.add(statusLabel);
        bottomPanel.add(Box.createVerticalStrut(8));

        // Swipe button - highlighted primary action
        swipeBtn = new JButton("QUẸT THẺ");
        swipeBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        swipeBtn.setPreferredSize(new Dimension(650, 45));
        swipeBtn.setMaximumSize(new Dimension(650, 45));
        swipeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        swipeBtn.setBackground(new Color(25, 118, 210));
        swipeBtn.setForeground(Color.WHITE);
        swipeBtn.setFocusPainted(false);
        swipeBtn.setBorder(BorderFactory.createRaisedBevelBorder());
        swipeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        swipeBtn.addActionListener(e -> swipeCard());
        bottomPanel.add(swipeBtn);
        bottomPanel.add(Box.createVerticalStrut(10));

        // Other buttons grid - improved layout
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new GridLayout(3, 3, 8, 8));
        buttonRow.setBackground(new Color(248, 250, 252));
        buttonRow.setMaximumSize(new Dimension(650, 100));

        personalInfoBtn = createModernButton("Thông Tin Cá Nhân", new Color(156, 39, 176));
        personalInfoBtn.addActionListener(e -> showPersonalInfo());
        buttonRow.add(personalInfoBtn);

        renewBtn = createModernButton(" Gia hạn gói tập", new Color(33, 150, 243));
        renewBtn.addActionListener(e -> renewPackage());
        buttonRow.add(renewBtn);

        changePinBtn = createModernButton("Đổi PIN", new Color(244, 67, 54));
        changePinBtn.addActionListener(e -> changePin());
        buttonRow.add(changePinBtn);

        purchaseBtn = createModernButton("Mua Hàng", new Color(76, 175, 80));
        purchaseBtn.addActionListener(e -> purchaseItem());
        buttonRow.add(purchaseBtn);

        topupBtn = createModernButton("Nạp Tiền", new Color(255, 152, 0));
        topupBtn.addActionListener(e -> topupBalance());
        buttonRow.add(topupBtn);

        // Check-in button: trừ 1 ngày/lần/ngày
        checkinBtn = createModernButton("Check-in", new Color(0, 150, 136));
        checkinBtn.addActionListener(e -> checkIn());
        buttonRow.add(checkinBtn);

        JButton logoutBtn = createModernButton("Thoát", new Color(120, 120, 120));
        logoutBtn.addActionListener(e -> {
        currentCard = null;
        disableButtons();
        statusLabel.setText("✓ Sẵn sàng");
        statusLabel.setForeground(new Color(60, 180, 60));
        infoArea.setText("Vui lòng nhấn 'Quẹt Thẻ' để bắt đầu\n");
    });

        buttonRow.add(logoutBtn);

        bottomPanel.add(buttonRow);
        add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);
    }
    private boolean verifyPinDialog() {
    JPasswordField pinField = new JPasswordField();
    int opt = JOptionPane.showConfirmDialog(
        this,
        new Object[]{"🔐 Nhập PIN (6 chữ số):", pinField},
        "Xác Thực PIN",
        JOptionPane.OK_CANCEL_OPTION
    );
    if (opt != JOptionPane.OK_OPTION) return false;

    try {
        String pinStr = new String(pinField.getPassword());
        // Kiểm tra phải đúng 6 chữ số
        if (!pinStr.matches("\\d{6}")) {
            throw new NumberFormatException("PIN phải là 6 chữ số");
        }
        String pin = pinStr;

        ResponseAPDU r = pcsc.transmit(
            CardHelper.buildVerifyPinCommand(pin)
        );

        if (r.getSW() != 0x9000) {
            // Cập nhật retry counter vào DB dựa trên SW
            try {
                short retries;
                if (r.getSW() == 0x6983) {
                    retries = 0; // locked
                } else if ((r.getSW() & 0xFFF0) == 0x63C0) {
                    retries = (short) (r.getSW() & 0x000F);
                } else {
                    retries = -1; // unknown
                }
                if (currentCard != null && currentCard.userId > 0 && retries >= 0) {
                    new MembersDao().updatePinRetry(currentCard.userId, retries);
                }
            } catch (Exception ignored) {}

            JOptionPane.showMessageDialog(
                this,
                CardHelper.parsePinStatus(r.getSW()),
                "PIN Sai",
                JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
        
        // Update currentCard with decrypted data from VERIFY_PIN response
        currentCard = CardHelper.parseVerifyPinResponse(r, pin);
        // Đồng bộ retry counter về 5 khi verify thành công
        try {
            if (currentCard != null && currentCard.userId > 0) {
                new MembersDao().updatePinRetry(currentCard.userId, (short)5);
            }
        } catch (Exception ignored) {}
        // ✅ LƯU PIN để các thao tác WRITE sau dùng đúng PIN
        currentCard.pin = pin;
        displayCardInfo();
        
        return true;
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "❌ " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        return false;
    }
}

    /**
     * Helper method to create modern styled buttons
     */
    private JButton createModernButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setEnabled(false);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (btn.isEnabled()) btn.setBackground(brighten(bgColor, 20));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (btn.isEnabled()) btn.setBackground(bgColor);
            }
        });
        return btn;
    }

    /**
     * Helper to brighten color on hover
     */
    private Color brighten(Color c, int amount) {
        int r = Math.min(255, c.getRed() + amount);
        int g = Math.min(255, c.getGreen() + amount);
        int b = Math.min(255, c.getBlue() + amount);
        return new Color(r, g, b);
    }

    private void disableButtons() {
        personalInfoBtn.setEnabled(false);
        renewBtn.setEnabled(false);
        changePinBtn.setEnabled(false);
        purchaseBtn.setEnabled(false);
        topupBtn.setEnabled(false);
        checkinBtn.setEnabled(false);
    }

    private void swipeCard() {
        infoArea.setText("");
        statusLabel.setText("Vui lòng đặt thẻ vào đúng vị trí...");
        statusLabel.setForeground(new Color(200, 100, 0));
        swipeBtn.setEnabled(false);

        new Thread(() -> {
            try {
                Thread.sleep(500); // Delay để người dùng có thể đặt thẻ vào

                pcsc.connectFirstPresentOrFirst();
                infoArea.append("[OK] Kết nối thẻ thành công!\n\n");

                // Select applet - ✅ AID đã fix: 26 12 20 03 03 00 (6 bytes)
                javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                        new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
                javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
                if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                    infoArea.append("[LỖI] Không thể kết nối ứng dụng trên thẻ (SW: 0x" + 
                        Integer.toHexString(selectResp.getSW()).toUpperCase() + ")\n");
                    infoArea.append("[INFO] Đảm bảo applet đã được install với AID: 26 12 20 03 03 00\n");
                    statusLabel.setText("Lỗi: Thẻ không hợp lệ");
                    statusLabel.setForeground(Color.RED);
                    swipeBtn.setEnabled(true);
                    return;
                }

                // Read card data
                javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
                javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);                
                // Handle 6C xx (Wrong LE) - retry with correct length
                if ((readResp.getSW() & 0xFF00) == 0x6C00) {
                    int correctLE = readResp.getSW2();
                    infoArea.append("[INFO] Applet yêu cầu LE=" + correctLE + " bytes, retry...\n");
                    readCmd = new javax.smartcardio.CommandAPDU(0x00, 0x11, 0x00, 0x00, correctLE);
                    readResp = pcsc.transmit(readCmd);
                }
                                if ((readResp.getSW() & 0xFF00) != 0x9000) {
                    infoArea.append("[LỖI] Không thể đọc dữ liệu thẻ - SW: " + Integer.toHexString(readResp.getSW()) + "\n");
                    statusLabel.setText("Lỗi: Đọc dữ liệu thất bại");
                    statusLabel.setForeground(Color.RED);
                    swipeBtn.setEnabled(true);
                    return;
                }

                byte[] responseData = readResp.getData();
                infoArea.append("[DEBUG] Response length: " + responseData.length + " bytes\n");
                infoArea.append("[DEBUG] Response HEX: " + PcscClient.toHex(responseData) + "\n");

                // Parse safe view (no PII) - full details will appear after PIN verify
                currentCard = CardHelper.parseReadResponse(responseData);
                infoArea.append("[INFO] UserID: " + currentCard.userId + "\n");

                // ===== XÁC THỰC PIN =====
                // Check if card is permanently locked
                if (currentCard.isLocked()) {
                JOptionPane.showMessageDialog(this, "Thẻ đã bị khóa!");
                return;
            }

                // Yêu cầu nhập PIN ngay sau khi quẹt thẻ
                SwingUtilities.invokeLater(() -> {
                    if (!verifyPinDialog()) {
                        infoArea.append("[HỦY] Xác thực PIN thất bại\n");
                        statusLabel.setText("Thất bại: Sai PIN");
                        statusLabel.setForeground(Color.RED);
                        swipeBtn.setEnabled(true);
                        return;
                    }

                    infoArea.append("✅ PIN chính xác!\n\n");
                    infoArea.append("✅ PIN chính xác!\n\n");

                    // Broadcast card info to Staff window
                    CardEventBroadcaster.getInstance().broadcastCardSwipe(currentCard);

                    // Display card info on customer window
                    displayCardInfo();

                    statusLabel.setText("Quẹt thẻ thành công!");
                    statusLabel.setForeground(new Color(50, 150, 50));

                    personalInfoBtn.setEnabled(true);
                    renewBtn.setEnabled(true);
                    changePinBtn.setEnabled(true);
                    purchaseBtn.setEnabled(true);
                    topupBtn.setEnabled(true);
                    checkinBtn.setEnabled(true);
                });

            } catch (Exception ex) {
                infoArea.append("[LỖI] " + ex.getMessage() + "\n");
                statusLabel.setText("Lỗi: " + ex.getMessage());
                statusLabel.setForeground(Color.RED);
            } finally {
                swipeBtn.setEnabled(true);
            }
        }).start();
    }

    private void checkIn() {
        if (currentCard == null || currentCard.userId <= 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
            return;
        }
        try {
            MembersDao dao = new MembersDao();
            MemberRecord rec = dao.getByUserId(currentCard.userId);
            java.time.LocalDate today = java.time.LocalDate.now();
            if (rec != null && rec.lastCheckinDate != null) {
                try {
                    java.time.LocalDate last = java.time.LocalDate.parse(rec.lastCheckinDate);
                    if (last != null && last.equals(today)) {
                        JOptionPane.showMessageDialog(this, "Hôm nay đã check-in. Không trừ ngày.");
                        return;
                    }
                } catch (Exception ignored) {}
            }

            if (currentCard.expiryDays <= 0) {
                JOptionPane.showMessageDialog(this, "Thẻ đã hết hạn. Không thể check-in.");
                return;
            }

            // Xác thực PIN để ghi thẻ an toàn
            if (!verifyPinDialog()) {
                infoArea.append("[HỦY] Check-in: Không thể xác thực PIN\n");
                return;
            }

            // Trừ 1 ngày trên thẻ (expiryDays)
            currentCard.expiryDays = (short)(currentCard.expiryDays - 1);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(CardHelper.buildWriteCommand(currentCard));
            if ((writeResp.getSW() & 0xFF00) != 0x9000) {
                infoArea.append("[LỖI] Ghi thẻ check-in thất bại\n");
                JOptionPane.showMessageDialog(this, "Lỗi ghi thẻ khi check-in");
                // Rollback in memory
                currentCard.expiryDays = (short)(currentCard.expiryDays + 1);
                return;
            }

            // Cập nhật DB: giảm expiry_date 1 ngày và lưu last_checkin_date
            java.time.LocalDate newExpiry = (rec != null && rec.expiryDate != null) ? rec.expiryDate.minusDays(1) : null;
            try {
                dao.updateExpiryAndCheckin(currentCard.userId, newExpiry, today.toString());
                infoArea.append("[DB] Đã cập nhật check-in hôm nay\n");
            } catch (Exception dbEx) {
                infoArea.append("[DB] Lỗi cập nhật check-in: " + dbEx.getMessage() + "\n");
            }

            // Hiển thị
            infoArea.append("✅ Check-in thành công. Đã trừ 1 ngày. Còn: " + currentCard.expiryDays + " ngày\n");
            CardEventBroadcaster.getInstance().broadcastCardSwipe(currentCard);
        } catch (Exception ex) {
            infoArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }

    private void displayCardInfo() {
        SwingUtilities.invokeLater(() -> {
            infoArea.setText("");
            infoArea.append("==== THÔNG TIN THẺ ====\n\n");
            infoArea.append("ID Thẻ: " + currentCard.userId + "\n");
            if (currentCard.fullName != null && !currentCard.fullName.isEmpty()) {
                infoArea.append("Họ Tên: " + currentCard.fullName + "\n");
            }
            if (currentCard.dobYear > 0) {
                infoArea.append("Ngày Sinh: " + currentCard.getDobString() + "\n");
            }
            infoArea.append("Số Dư: " + String.format("%,d VND", currentCard.balance) + "\n");
            infoArea.append("Hạn Sử Dụng: " + currentCard.expiryDays + " ngày\n\n");

            if (currentCard.expiryDays <= 0) {
                infoArea.append("*** THẺ ĐÃ HẾT HẠN ***\n");
            } else if (currentCard.expiryDays <= 7) {
                infoArea.append("*** THẺ SẮP HẾT HẠN ***\n");
            } else {
                infoArea.append("[OK] Thẻ còn hiệu lực\n");
            }
            infoArea.append("\nNhấn 'Thông Tin Cá Nhân' để xem thêm chi tiết\n");
        });
    }

    private void showPersonalInfo() {
        if (currentCard == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
            return;
        }
        JPanel panel = new JPanel(new BorderLayout(10,10));
        JTextArea text = new JTextArea();
        text.setEditable(false);
        StringBuilder info = new StringBuilder();
        info.append("=== THÔNG TIN CÁ NHÂN ===\n\n");
        info.append("ID: ").append(currentCard.userId).append("\n");
        info.append("PIN: ****\n");
        if (currentCard.fullName != null && !currentCard.fullName.isEmpty()) {
            info.append("Họ Tên: ").append(currentCard.fullName).append("\n");
        }
        if (currentCard.dobYear > 0) {
            info.append("Ngày Sinh: ").append(currentCard.getDobString()).append("\n");
        }
        // CCCD từ thẻ (đã decrypt)
        if (currentCard.cccd != null && !currentCard.cccd.isEmpty()) {
            info.append("CCCD: ").append(currentCard.cccd).append("\n");
        }
        short retriesToShow = currentCard.pinRetry;
        MemberRecord rec = null;
        try { rec = new MembersDao().getByUserId(currentCard.userId); } catch (Exception ignored) {}
        if (rec != null) {
            retriesToShow = rec.pinretry;
        }
        info.append("Số lần thử PIN còn lại: ").append(retriesToShow).append("/5\n");
        info.append("\nSố Dư: ").append(currentCard.balance).append(" VND\n");
        info.append("Hạn Tập: ").append(currentCard.expiryDays).append(" ngày\n");
        text.setText(info.toString());
        panel.add(new JScrollPane(text), BorderLayout.CENTER);

        // Avatar hiển thị từ DB nếu có
        if (rec != null && rec.avatarData != null && rec.avatarData.length > 0) {
            try {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(rec.avatarData);
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(bais);
                Image scaled = img.getScaledInstance(128, 128, Image.SCALE_SMOOTH);
                JLabel avatarLabel = new JLabel(new ImageIcon(scaled));
                avatarLabel.setBorder(BorderFactory.createTitledBorder("Ảnh Đại Diện"));
                panel.add(avatarLabel, BorderLayout.EAST);
            } catch (Exception ignored) {}
        }

        JOptionPane.showMessageDialog(this, panel, "Thông Tin Cá Nhân", JOptionPane.INFORMATION_MESSAGE);
    }

    private void renewPackage() {
        if (currentCard == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
            return;
        }

        // Bảng giá gói tập
        String[] packages = {
            "1 Ngày - 50,000 VND",
            "1 Tuần - 300,000 VND",
            "1 Tháng - 1,000,000 VND",
            "3 Tháng - 2,700,000 VND",
            "1 Năm - 10,000,000 VND"
        };
        
        String selected = (String) JOptionPane.showInputDialog(
            this, 
            "Chọn gói gia hạn:", 
            "Gia Hạn Gói Tập", 
            JOptionPane.QUESTION_MESSAGE, 
            null, 
            packages, 
            packages[0]
        );
        
        if (selected == null) return;

        int daysToAdd = 0;
        int price = 0;
        
        if (selected.contains("1 Ngày")) {
            daysToAdd = 1;
            price = 50000;
        } else if (selected.contains("1 Tuần")) {
            daysToAdd = 7;
            price = 300000;
        } else if (selected.contains("1 Tháng") && !selected.contains("3")) {
            daysToAdd = 30;
            price = 1000000;
        } else if (selected.contains("3 Tháng")) {
            daysToAdd = 90;
            price = 2700000;
        } else if (selected.contains("1 Năm")) {
            daysToAdd = 365;
            price = 10000000;
        }
        
        // Kiểm tra số dư
        if (currentCard.balance < price) {
            JOptionPane.showMessageDialog(
                this, 
                "❌ Số dư không đủ!\n💰 Cần: " + String.format("%,d VND", price) + "\n💳 Có: " + String.format("%,d VND", currentCard.balance), 
                "Quá nghèo rồi!", 
                JOptionPane.ERROR_MESSAGE
            );
            infoArea.append("[LỖI] Số dư không đủ để gia hạn\n");
            return;
        }
        
        // Xác nhận thanh toán
        int confirm = JOptionPane.showConfirmDialog(
            this, 
            "Xác nhận gia hạn:\n" +
            "Gói: " + selected + "\n" +
            "Giá: " + String.format("%,d VND", price) + "\n" +
            "Thêm: " + daysToAdd + " ngày\n" +
            "Số dư sau: " + String.format("%,d VND", currentCard.balance - price), 
            "Xác Nhận", 
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm != JOptionPane.YES_OPTION) return;

        // Xác thực PIN trước khi ghi
        if (!verifyPinDialog()) {
            infoArea.append("[HỦY] Không thể xác thực PIN\n");
            return;
        }

        try {
            // Trừ tiền và cộng ngày
            currentCard.balance = currentCard.balance - price;
            currentCard.expiryDays = (short) (currentCard.expiryDays + daysToAdd);

            infoArea.append("\n[TIẾN HÀNH] Gia hạn " + selected + "...\n");

            // Write updated card
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(currentCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);

            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                infoArea.append("[OK] Gia hạn thành công!\n");
                infoArea.append("Đã trừ: " + String.format("%,d VND", price) + "\n");
                infoArea.append("Gia hạn: +" + daysToAdd + " ngày\n");
                infoArea.append("Hạn mới: " + currentCard.expiryDays + " ngày\n");
                infoArea.append("Số dư còn: " + String.format("%,d VND", currentCard.balance) + "\n");
                displayCardInfo();
          
                // �🔄 Broadcast để Staff thấy thay đổi
                CardEventBroadcaster.getInstance().broadcastCardSwipe(currentCard);

                // [DB] Log renew and update balance/expiry
                try {
                    TransactionsDao txDao = new TransactionsDao();
                    txDao.logRenew(currentCard.userId, daysToAdd, price);
                    MembersDao mDao = new MembersDao();
                    java.time.LocalDate expiryDate = currentCard.expiryDays > 0 ? java.time.LocalDate.now().plusDays(currentCard.expiryDays) : null;
                    mDao.updateBalanceAndExpiry(currentCard.userId, currentCard.balance, expiryDate);
                    infoArea.append("[DB] Đã ghi gia hạn vào Database\n");
                } catch (Exception dbEx) {
                    infoArea.append("[DB] Lỗi ghi gia hạn: " + dbEx.getMessage() + "\n");
                    // Fail-safe: ghi log khẩn cấp nếu DB lỗi
                    try {
                        FileLogger.logRenew(currentCard.userId, price, currentCard.balance, daysToAdd);
                        infoArea.append("[LOG] Đã lưu emergency log (RENEW) để đối soát.\n");
                        JOptionPane.showMessageDialog(this, "⚠️ Giao dịch đã ghi lên thẻ nhưng DB lỗi. Đã lưu log khẩn cấp!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    } catch (Exception ignored) {}
                }
            } else {
                infoArea.append("[LỖI] Gia hạn thất bại\n");
                // Rollback
                currentCard.balance = currentCard.balance + price;
                currentCard.expiryDays = (short) (currentCard.expiryDays - daysToAdd);
            }
        } catch (Exception ex) {
            infoArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }

    private void changePin() {
    if (currentCard == null) {
        JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
        return;
    }

    try {
        // 🔐 Nhập PIN hiện tại
        JPasswordField oldPinField = new JPasswordField();
        int opt = JOptionPane.showConfirmDialog(
            this,
            new Object[]{"🔐 PIN hiện tại (6 chữ số):", oldPinField},
            "Xác thực PIN",
            JOptionPane.OK_CANCEL_OPTION
        );
        if (opt != JOptionPane.OK_OPTION) return;

        String oldPin;
        try {
            String pinStr = new String(oldPinField.getPassword());
            if (!pinStr.matches("\\d{6}")) {
                throw new NumberFormatException("PIN phải là 6 chữ số");
            }
            oldPin = pinStr;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ PIN phải là 6 chữ số (000000-999999)");
            return;
        }

        // ✅ VERIFY PIN cũ và lấy dữ liệu đã giải mã
        javax.smartcardio.CommandAPDU verifyCmd =
                CardHelper.buildVerifyPinCommand(oldPin);
        javax.smartcardio.ResponseAPDU verifyResp = pcsc.transmit(verifyCmd);

        if (verifyResp.getSW() != 0x9000) {
            // Cập nhật retry counter vào DB nếu sai PIN
            try {
                short retries;
                if (verifyResp.getSW() == 0x6983) {
                    retries = 0;
                } else if ((verifyResp.getSW() & 0xFFF0) == 0x63C0) {
                    retries = (short) (verifyResp.getSW() & 0x000F);
                } else {
                    retries = -1;
                }
                if (currentCard != null && currentCard.userId > 0 && retries >= 0) {
                    new MembersDao().updatePinRetry(currentCard.userId, retries);
                }
            } catch (Exception ignored) {}

            String status = CardHelper.parsePinStatus(verifyResp.getSW());
            JOptionPane.showMessageDialog(this, "❌ " + status);
            return;
        }
        // Thành công: reset về 5
        try { if (currentCard != null && currentCard.userId > 0) new MembersDao().updatePinRetry(currentCard.userId, (short)5); } catch (Exception ignored) {}
        
        // 💾 Lưu dữ liệu đã giải mã (sẽ re-encrypt với PIN mới)
        CardData decryptedData = CardHelper.parseVerifyPinResponse(verifyResp, oldPin);
        infoArea.append("[OK] Đã lấy dữ liệu: Balance=" + decryptedData.balance + ", Expiry=" + decryptedData.expiryDays + "\n");

        // 🔁 Nhập PIN mới
        JPasswordField newPinField = new JPasswordField();
        opt = JOptionPane.showConfirmDialog(
            this,
            new Object[]{"PIN mới (6 chữ số):", newPinField},
            "Đổi PIN",
            JOptionPane.OK_CANCEL_OPTION
        );
        if (opt != JOptionPane.OK_OPTION) return;

        String newPin;
        try {
            String pinStr = new String(newPinField.getPassword());
            if (!pinStr.matches("\\d{6}")) {
                throw new NumberFormatException("PIN phải là 6 chữ số");
            }
            newPin = pinStr;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ PIN mới phải là 6 chữ số (000000-999999)");
            return;
        }

        // ✅ Kiểm tra PIN mới không được trùng PIN cũ
        if (newPin.equals(oldPin)) {
            JOptionPane.showMessageDialog(this, "❌ PIN mới không được trùng với PIN cũ!");
            return;
        }

        // 🔄 Gọi CHANGE_PIN command trên thẻ
        infoArea.append("\n[TIẾN HÀNH] Đổi PIN...\n");

        javax.smartcardio.CommandAPDU changeCmd =
                CardHelper.buildChangePinCommand(oldPin, newPin);
        javax.smartcardio.ResponseAPDU changeResp = pcsc.transmit(changeCmd);

        if (changeResp.getSW() == 0x9000) {
            infoArea.append("[OK] Đổi PIN thành công!\n");
            infoArea.append("[INFO] Card đã tự động re-encrypt dữ liệu với PIN mới\n");
            // Sau khi đổi PIN thành công, retry counter đã về 5 trên thẻ
            try { if (currentCard != null && currentCard.userId > 0) new MembersDao().updatePinRetry(currentCard.userId, (short)5); } catch (Exception ignored) {}
            
            // ✅ ĐỌC LẠI THẺ thay vì VERIFY (vì session vẫn hợp lệ)
            try {
                javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
                javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
                
                if (readResp.getSW() == 0x9000) {
                    // ✅ DEBUG: In ra raw bytes để kiểm tra
                    byte[] rawData = readResp.getData();
                    infoArea.append("[DEBUG] Raw response length: " + rawData.length + " bytes\n");
                    infoArea.append("[DEBUG] Raw response (hex): " + PcscClient.toHex(rawData) + "\n");
                    infoArea.append("[DEBUG] Encrypted balance bytes [2-5]: ");
                    for (int i = 2; i <= 5; i++) {
                        infoArea.append(String.format("%02X ", rawData[i]));
                    }
                    infoArea.append("\n");
                    
                    // Parse và decrypt với PIN mới
                    currentCard = CardHelper.parseReadResponse(rawData, newPin);
                    infoArea.append("[DEBUG] Parsed balance: " + currentCard.balance + " VND\n");
                    infoArea.append("[DEBUG] Parsed expiry: " + currentCard.expiryDays + " days\n");
                    
                    // ✅ LƯU PIN MỚI để các thao tác WRITE sau dùng đúng PIN
                    currentCard.pin = newPin;
                    displayCardInfo();
                    
                    JOptionPane.showMessageDialog(this, "✅ Đổi PIN thành công!");
                } else {
                    infoArea.append("[CẢNH BÁO] Không thể đọc thẻ, vui lòng thử lại\n");
                    JOptionPane.showMessageDialog(this, "✅ Đổi PIN thành công!\n⚠️ Vui lòng quẹt thẻ lại để xem dữ liệu");
                }
            } catch (Exception ex) {
                infoArea.append("[LỖI] " + ex.getMessage() + "\n");
                JOptionPane.showMessageDialog(this, "✅ Đổi PIN thành công!\n⚠️ " + ex.getMessage());
            }

            // 🔄 Sync cho Staff
            CardEventBroadcaster.getInstance()
                    .broadcastCardSwipe(currentCard);
        } else {
            String status = CardHelper.parsePinStatus(changeResp.getSW());
            infoArea.append("[LỖI] Đổi PIN thất bại: " + status + "\n");
            JOptionPane.showMessageDialog(this, "❌ Đổi PIN thất bại: " + status);
            // Nếu lỗi do security status (chưa verify) thì không thay đổi retry.
            // Nếu có mã 63Cx thì cập nhật retry theo SW.
            try {
                short retries = -1;
                if ((changeResp.getSW() & 0xFFF0) == 0x63C0) {
                    retries = (short) (changeResp.getSW() & 0x000F);
                } else if (changeResp.getSW() == 0x6983) {
                    retries = 0;
                }
                if (retries >= 0 && currentCard != null && currentCard.userId > 0) {
                    new MembersDao().updatePinRetry(currentCard.userId, retries);
                }
            } catch (Exception ignored) {}
        }

    } catch (Exception ex) {
        infoArea.append("[LỖI] " + ex.getMessage() + "\n");
    }
}


    private void purchaseItem() {
        if (currentCard == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
            return;
        }
        StoreManager store = new StoreManager();
        java.util.List<StoreItem> cart = new java.util.ArrayList<>();

        // Dialog chọn hàng
        JFrame shopFrame = new JFrame("Cửa Hàng Gym");
        shopFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        shopFrame.setSize(800, 600);
        shopFrame.setLocationRelativeTo(null);
        shopFrame.setLayout(new BorderLayout(10, 10));

        // Panel trái: danh sách hàng
        JPanel leftPanel = new JPanel(new BorderLayout());
        JLabel itemsLabel = new JLabel("DANH SÁCH HÀNG HÓA");
        itemsLabel.setFont(new Font("Arial", Font.BOLD, 12));
        leftPanel.add(itemsLabel, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (StoreItem item : store.getItems()) {
            listModel.addElement(item.name + " - " + item.price + "₫");
        }

        JList<String> itemList = new JList<>(listModel);
        itemList.setSelectedIndex(0);
        JScrollPane listScroll = new JScrollPane(itemList);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        // Button thêm vào giỏ
        JPanel addBtnPanel = new JPanel();
        JLabel quantityLabel = new JLabel("SL:");
        JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JButton addBtn = new JButton("Thêm Vào Giỏ");
        addBtn.addActionListener(e -> {
            int idx = itemList.getSelectedIndex();
            if (idx >= 0) {
                StoreItem item = store.getItems().get(idx);
                int qty = (Integer) quantitySpinner.getValue();
                
                boolean found = false;
                for (StoreItem cartItem : cart) {
                    if (cartItem.id == item.id) {
                        cartItem.quantity += qty;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    StoreItem newItem = new StoreItem(item.id, item.name, item.price);
                    newItem.quantity = qty;
                    cart.add(newItem);
                }
            }
        });

        addBtnPanel.add(quantityLabel);
        addBtnPanel.add(quantitySpinner);
        addBtnPanel.add(addBtn);
        leftPanel.add(addBtnPanel, BorderLayout.SOUTH);

        // Panel phải: giỏ hàng
        JPanel rightPanel = new JPanel(new BorderLayout());
        JLabel cartLabel = new JLabel("GIỎ HÀNG");
        cartLabel.setFont(new Font("Arial", Font.BOLD, 12));
        rightPanel.add(cartLabel, BorderLayout.NORTH);

        DefaultListModel<String> cartModel = new DefaultListModel<>();
        JList<String> cartList = new JList<>(cartModel);
        JScrollPane cartScroll = new JScrollPane(cartList);
        rightPanel.add(cartScroll, BorderLayout.CENTER);

        // Panel chỉnh giỏ
        JPanel cartBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton updateBtn = new JButton("Cập Nhật SL");
        updateBtn.addActionListener(e -> {
            int cartIdx = cartList.getSelectedIndex();
            if (cartIdx >= 0) {
                String newQty = JOptionPane.showInputDialog(shopFrame, "Số lượng mới:", "1");
                if (newQty != null) {
                    try {
                        int qty = Integer.parseInt(newQty);
                        if (qty <= 0) {
                            cart.remove(cartIdx);
                        } else {
                            cart.get(cartIdx).quantity = qty;
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(shopFrame, "Nhập số lượng hợp lệ");
                    }
                }
            }
        });

        JButton removeBtn = new JButton("Xóa");
        removeBtn.addActionListener(e -> {
            int cartIdx = cartList.getSelectedIndex();
            if (cartIdx >= 0) {
                cart.remove(cartIdx);
            }
        });

        cartBtnPanel.add(updateBtn);
        cartBtnPanel.add(removeBtn);
        rightPanel.add(cartBtnPanel, BorderLayout.SOUTH);

        // Panel giữa: cập nhật giỏ
        JPanel centerPanel = new JPanel();
        centerPanel.setPreferredSize(new Dimension(1, 1));
        shopFrame.add(centerPanel, BorderLayout.CENTER);

        // Panel dưới: tổng tiền + thanh toán
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JLabel totalLabel = new JLabel("Tổng tiền: 0 VND");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JButton checkoutBtn = new JButton("THANH TOÁN");
        checkoutBtn.setFont(new Font("Arial", Font.BOLD, 12));
        checkoutBtn.setBackground(new Color(100, 200, 100));
        checkoutBtn.setForeground(Color.WHITE);

        checkoutBtn.addActionListener(e -> {
            int totalPrice = store.getTotalPrice(cart);
            if (cart.isEmpty()) {
                JOptionPane.showMessageDialog(shopFrame, "Giỏ hàng trống!");
                return;
            }

            if (currentCard.balance < totalPrice) {
                JOptionPane.showMessageDialog(shopFrame, 
                    "❌ Số dư không đủ!\nCần: " + String.format("%,d VND", totalPrice) + 
                    "\nCó: " + String.format("%,d VND", currentCard.balance));
                return;
            }

            // Hiển thị bill
            StringBuilder bill = new StringBuilder();
            bill.append("=== HOÁ ĐƠN ===\n");
            for (StoreItem item : cart) {
                bill.append(item.name).append(" x").append(item.quantity)
                    .append(" = ").append(String.format("%,d", item.quantity * item.price)).append("₫\n");
            }
            bill.append("---\n");
            bill.append("TỔNG CỘNG: " + String.format("%,d", totalPrice) + "₫\n\n");
            bill.append("Vui lòng chờ nhân viên xác nhận...");

            infoArea.append("\n[CHỜ XÁC NHẬN] Gửi đơn hàng:\n");
            for (StoreItem item : cart) {
                infoArea.append("  - " + item.name + " x" + item.quantity + "\n");
            }
            infoArea.append("Tổng: " + String.format("%,d VND", totalPrice) + "\n");
            infoArea.append("[⏳] Đang chờ nhân viên xác nhận...\n");
            
            // Tạo danh sách items để gửi approval
            List<CardEventBroadcaster.CartItem> approvalItems = new ArrayList<>();
            for (StoreItem item : cart) {
                approvalItems.add(new CardEventBroadcaster.CartItem(item, item.quantity));
            }
            
            final int finalTotalPrice = totalPrice;
            
            // Chạy approval trong background thread
            new Thread(() -> {
                boolean approved = CardEventBroadcaster.getInstance()
                    .requestPurchaseApproval(approvalItems, finalTotalPrice);
                
                SwingUtilities.invokeLater(() -> {
                    if (!approved) {
                        infoArea.append("[✗ TỪ CHỐI] Nhân viên từ chối đơn hàng!\n");
                        JOptionPane.showMessageDialog(
                            shopFrame, 
                            "❌ Nhân viên từ chối đơn hàng!", 
                            "Thất Bại", 
                            JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }
                    
                    infoArea.append("[✓ CHẤP NHẬN] Nhân viên đã xác nhận!\n");
                    if (!verifyPinDialog()) {
                        infoArea.append("[HỦY] Xác thực PIN thất bại\n");
                        return;
}
                    // ✅ Trừ tiền ĐÚNG - SAU KHI được xác nhận
                    currentCard.balance = currentCard.balance - finalTotalPrice;
                    
                    try {
                        javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(currentCard);
                        javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);

                        if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                            infoArea.append("[OK] Thanh toán thành công!\n");
                            infoArea.append("Số dư mới: " + String.format("%,d VND", currentCard.balance) + "\n");
                            displayCardInfo();

                            // Firebase sync removed

                            // 🔄 Broadcast để Staff thấy số dư mới
                            CardEventBroadcaster.getInstance().broadcastCardSwipe(currentCard);

                            // [DB] Log purchase and update balance
                            try {
                                TransactionsDao txDao = new TransactionsDao();
                                java.util.List<CardEventBroadcaster.CartItem> itemsForDb = approvalItems;
                                txDao.logPurchase(currentCard.userId, itemsForDb, finalTotalPrice);
                                MembersDao mDao = new MembersDao();
                                java.time.LocalDate expiryDate = currentCard.expiryDays > 0 ? java.time.LocalDate.now().plusDays(currentCard.expiryDays) : null;
                                mDao.updateBalanceAndExpiry(currentCard.userId, currentCard.balance, expiryDate);
                                infoArea.append("[DB] Đã ghi giao dịch vào Database\n");
                            } catch (Exception dbEx) {
                                infoArea.append("[DB] Lỗi ghi giao dịch: " + dbEx.getMessage() + "\n");
                                try {
                                    FileLogger.logPurchase(currentCard.userId, finalTotalPrice, currentCard.balance, null);
                                    infoArea.append("[LOG] Đã lưu emergency log (PURCHASE) để đối soát.\n");
                                    JOptionPane.showMessageDialog(this, "⚠️ Giao dịch đã ghi lên thẻ nhưng DB lỗi. Đã lưu log khẩn cấp!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                                } catch (Exception ignored) {}
                            }
                            
                            JOptionPane.showMessageDialog(
                                shopFrame, 
                                "✅ Thanh toán thành công!\nSố dư còn: " + String.format("%,d VND", currentCard.balance), 
                                "Hoàn Tất", 
                                JOptionPane.INFORMATION_MESSAGE
                            );
                            
                            cart.clear();
                            shopFrame.dispose();
                        } else {
                            infoArea.append("[LỖI] Thanh toán thất bại\n");
                            // Rollback
                            currentCard.balance = currentCard.balance + finalTotalPrice;
                        }
                    } catch (Exception ex) {
                        infoArea.append("[LỖI] " + ex.getMessage() + "\n");
                        currentCard.balance = currentCard.balance + finalTotalPrice;
                    }
                });
            }).start();
        });

        bottomPanel.add(totalLabel, BorderLayout.WEST);
        bottomPanel.add(checkoutBtn, BorderLayout.EAST);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        shopFrame.add(leftPanel, BorderLayout.WEST);
        shopFrame.add(rightPanel, BorderLayout.EAST);
        shopFrame.add(bottomPanel, BorderLayout.SOUTH);

        // Cập nhật giỏ khi click item
        javax.swing.Timer updateTimer = new javax.swing.Timer(100, e -> {
            cartModel.clear();
            int total = 0;
            for (StoreItem item : cart) {
                cartModel.addElement(item.name + " x" + item.quantity + " = " + String.format("%,d", item.quantity * item.price) + "₫");
                total += item.quantity * item.price;
            }
            totalLabel.setText("Tổng tiền: " + String.format("%,d VND", total));
        });
        updateTimer.start();

        shopFrame.setVisible(true);
    }

    private void topupBalance() {
        if (currentCard == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước");
            return;
        }

        // Dialog nhập số tiền
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JComboBox<String> presetCombo = new JComboBox<>(new String[]{
            "100,000 VND", "200,000 VND", "500,000 VND", "1,000,000 VND", "Nhập tùy chỉnh"
        });
        JSpinner customSpinner = new JSpinner(new SpinnerNumberModel(100000, 10000, 10000000, 10000));

        panel.add(new JLabel("Mệnh giá:"));
        panel.add(presetCombo);
        panel.add(new JLabel("Số tiền (tuỳ chỉnh):"));
        panel.add(customSpinner);

        int option = JOptionPane.showConfirmDialog(this, panel, "Nạp Tiền", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        int amount = 0;
        String selected = (String) presetCombo.getSelectedItem();
        
        if ("Nhập tùy chỉnh".equals(selected)) {
            amount = (Integer) customSpinner.getValue();
        } else {
            switch (selected) {
                case "100,000 VND": amount = 100000; break;
                case "200,000 VND": amount = 200000; break;
                case "500,000 VND": amount = 500000; break;
                case "1,000,000 VND": amount = 1000000; break;
            }
        }

        infoArea.append("\n[TIẾN HÀNH] Nạp " + String.format("%,d VND", amount) + "...\n");
        
        // 💳 Chọn phương thức thanh toán
        String[] methods = {"Tiền Mặt", "QR Code"};
        String paymentMethod = (String) JOptionPane.showInputDialog(
            this, 
            "Chọn phương thức thanh toán:", 
            "Thanh Toán", 
            JOptionPane.QUESTION_MESSAGE, 
            null, 
            methods, 
            methods[0]
        );
        
        if (paymentMethod == null) {
            infoArea.append("[HỦY] Khách hàng huỷ giao dịch\n");
            return;
        }
        
        // Nếu chọn QR, hiển thị mã QR
        if (paymentMethod.contains("QR")) {
            try {
                java.io.File qrFile = new java.io.File("resources/qr-code.png");
                if (qrFile.exists()) {
                    javax.swing.ImageIcon qrIcon = new javax.swing.ImageIcon(qrFile.getAbsolutePath());
                    // Scale ảnh về 300x300
                    java.awt.Image scaledImage = qrIcon.getImage().getScaledInstance(300, 300, java.awt.Image.SCALE_SMOOTH);
                    qrIcon = new javax.swing.ImageIcon(scaledImage);
                    
                    JOptionPane.showMessageDialog(
                        this, 
                        qrIcon, 
                        "Quét Mã QR - Số tiền: " + String.format("%,d VND", amount), 
                        JOptionPane.PLAIN_MESSAGE
                    );
                    infoArea.append("[QR] Đã hiển thị mã QR cho khách hàng\n");
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "⚠ Không tìm thấy file QR code!\nĐặt file 'qr-code.png' vào thư mục 'resources'", 
                        "Thiếu File", 
                        JOptionPane.WARNING_MESSAGE
                    );
                    infoArea.append("[CẢNH BÁO] Không tìm thấy file QR\n");
                }
            } catch (Exception ex) {
                infoArea.append("[LỖI QR] " + ex.getMessage() + "\n");
            }
        } else {
            infoArea.append("[TIỀN MẶT] Nhận tiền mặt từ khách hàng\n");
        }
        
        // ⏳ Gửi yêu cầu xác nhận đến nhân viên
        infoArea.append("\n[CHỜ] Đang chờ nhân viên xác nhận...\n");
        
        final int finalAmount = amount;
        final String finalPaymentMethod = paymentMethod;
        
        // Chạy approval trong background thread để không block UI
        new Thread(() -> {

    boolean approved = CardEventBroadcaster.getInstance()
        .requestTopupApproval(finalAmount, finalPaymentMethod);

    if (!approved) {
        SwingUtilities.invokeLater(() ->
            infoArea.append("[✗ TỪ CHỐI] Nhân viên từ chối giao dịch\n")
        );
        return;
    }

    SwingUtilities.invokeLater(() -> {
        infoArea.append("[✓ CHẤP NHẬN] Nhân viên đã xác nhận!\n");
        
        // 🔐 VERIFY PIN – Phải chạy trong EDT để dialog hiển thị đúng
        if (!verifyPinDialog()) {
            infoArea.append("[HỦY] Xác thực PIN thất bại\n");
            return;
        }

        // 💾 WRITE – Sau khi PIN đã verify
        currentCard.balance += finalAmount;

        try {
            javax.smartcardio.CommandAPDU writeCmd =
                CardHelper.buildWriteCommand(currentCard);
            javax.smartcardio.ResponseAPDU writeResp =
                pcsc.transmit(writeCmd);

            if (writeResp.getSW() == 0x9000) {
                infoArea.append("[OK] Nạp tiền thành công!\n");
                infoArea.append("💰 Đã nạp: " + String.format("%,d VND", finalAmount) + "\n");
                infoArea.append("💳 Số dư mới: " + String.format("%,d VND", currentCard.balance) + "\n");
                displayCardInfo();
            
                
                CardEventBroadcaster.getInstance()
                    .broadcastCardSwipe(currentCard);

                // [DB] Log topup and update balance
                try {
                    TransactionsDao txDao = new TransactionsDao();
                    txDao.logTopup(currentCard.userId, finalAmount, finalPaymentMethod);
                    MembersDao mDao = new MembersDao();
                    java.time.LocalDate expiryDate = currentCard.expiryDays > 0 ? java.time.LocalDate.now().plusDays(currentCard.expiryDays) : null;
                    mDao.updateBalanceAndExpiry(currentCard.userId, currentCard.balance, expiryDate);
                    infoArea.append("[DB] Đã ghi giao dịch vào Database\n");
                } catch (Exception dbEx) {
                    infoArea.append("[DB] Lỗi ghi giao dịch: " + dbEx.getMessage() + "\n");
                    try {
                        FileLogger.logTopup(currentCard.userId, finalAmount, currentCard.balance);
                        infoArea.append("[LOG] Đã lưu emergency log (TOPUP) để đối soát.\n");
                        JOptionPane.showMessageDialog(this, "⚠️ Giao dịch đã ghi lên thẻ nhưng DB lỗi. Đã lưu log khẩn cấp!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    } catch (Exception ignored) {}
                }
            } else {
                infoArea.append("[LỖI] Nạp tiền thất bại (SW: " +
                    Integer.toHexString(writeResp.getSW()) + ")\n");
                // Rollback
                currentCard.balance -= finalAmount;
            }

        } catch (Exception e) {
            infoArea.append("[LỖI] " + e.getMessage() + "\n");
            // Rollback
            currentCard.balance -= finalAmount;
        }
    });

}).start();

    }

    public static void main(String[] args) {
        try {
            PcscClient pcsc = new PcscClient();
            SwingUtilities.invokeLater(() -> new CustomerWindow(pcsc));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Lỗi: " + e.getMessage());
        }
    }
}
