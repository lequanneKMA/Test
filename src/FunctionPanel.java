import javax.swing.*;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * Panel bên phải - Hiển thị chức năng theo role (Employee / Admin)
 * FIXED: Đọc thẻ sau khi tạo bằng cách verify PIN trước
 */
public class FunctionPanel extends JPanel {
    private final JTextArea logArea;
    private final JPanel controlPanel;
    private final JLabel roleLabel;
    private String currentRole;
    private final PcscClient pcsc;
    private CardData currentCard;

    public FunctionPanel(PcscClient pcsc) {
        this.pcsc = pcsc;
        this.currentRole = "EMPLOYEE"; // Mặc định

        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        // Register as card event listener
        CardEventBroadcaster.getInstance().addCardListener(card -> {
            SwingUtilities.invokeLater(() -> displayCardInfo(card));
        });
        
        // Register as purchase approval listener
        CardEventBroadcaster.getInstance().addPurchaseListener((items, totalPrice) -> {
            return showPurchaseApprovalDialog(items, totalPrice);
        });
        
        // Register as topup approval listener
        CardEventBroadcaster.getInstance().addTopupListener((amount, paymentMethod) -> {
            return showTopupApprovalDialog(amount, paymentMethod);
        });

        // Top: Tiêu đề vai trò - Modern gradient
        JPanel topPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(103, 58, 183), 
                                                           getWidth(), 0, new Color(156, 39, 176));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        topPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 12));
        roleLabel = new JLabel("👤 NHÂN VIÊN");
        roleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        roleLabel.setForeground(Color.WHITE);
        topPanel.add(roleLabel);
        topPanel.setPreferredSize(new Dimension(800, 55));
        add(topPanel, BorderLayout.NORTH);

        // Center: Log area - Modern styling
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(245, 245, 250));
        logArea.setForeground(new Color(30, 40, 50));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setText("Sẵn sàng\n");
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 210), 1));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: Control buttons - Modern layout
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        controlPanel.setBackground(new Color(248, 250, 252));
        controlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)));

        // Nút cho Employee (mặc định)
        addEmployeeButtons();

        add(controlPanel, BorderLayout.SOUTH);
    }

    private void addEmployeeButtons() {
        controlPanel.removeAll();
        logArea.setText("Nhân Viên: Quẹt thẻ khách để xem thông tin\n");

        JButton swipeBtn = createModernButton("Quẹt Thẻ Khách", new Color(33, 150, 243));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    private void addAdminButtons() {
        controlPanel.removeAll();
        logArea.setText("Admin: Tạo hoặc quẹt thẻ\n");

        JButton createBtn = createModernButton("Tạo Thẻ Mới", new Color(76, 175, 80));
        createBtn.addActionListener(e -> createNewCard());
        controlPanel.add(createBtn);

        JButton swipeBtn = createModernButton("Quẹt Thẻ", new Color(255, 152, 0));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);
        
        JButton deleteBtn = createModernButton("Xóa Thẻ", new Color(244, 67, 54));
        deleteBtn.addActionListener(e -> deleteCard());
        controlPanel.add(deleteBtn);
        
        JButton unlockBtn = createModernButton("Mở Khóa", new Color(255, 193, 7));
        unlockBtn.addActionListener(e -> unlockCard());
        controlPanel.add(unlockBtn);
        
        JButton resetPinBtn = createModernButton("Reset PIN", new Color(156, 39, 176));
        resetPinBtn.addActionListener(e -> resetPin());
        controlPanel.add(resetPinBtn);
        
        JButton editInfoBtn = createModernButton("Sửa Thông Tin", new Color(63, 81, 181));
        editInfoBtn.addActionListener(e -> editMemberInfo());
        controlPanel.add(editInfoBtn);

        JButton viewMembersBtn = createModernButton("Xem Thành Viên (DB)", new Color(0, 121, 107));
        viewMembersBtn.addActionListener(e -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(this);
            java.awt.Frame f = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
            new MembersTableDialog(f).setVisible(true);
        });
        controlPanel.add(viewMembersBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    public void switchRole(String role) {
        this.currentRole = role;

        if (role.equals("ADMIN")) {
            roleLabel.setText("ADMIN");
            addAdminButtons();
        } else {
            roleLabel.setText("NHÂN VIÊN");
            addEmployeeButtons();
        }
    }

    private void readCustomerCard() {
        logArea.setText("");
        logArea.append("[TIẾN HÀNH] Đặt thẻ vào đúng vị trí...\n");

        new Thread(() -> {
            try {
                Thread.sleep(500);
                pcsc.connectFirstPresentOrFirst();
                logArea.append("[OK] Kết nối thẻ thành công!\n\n");

                // Select applet
                javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                        new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
                javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);

                if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không kết nối được applet\n");
                    return;
                }

                // Read
                javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
                javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);

                if ((readResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không đọc được dữ liệu\n");
                    return;
                }

                currentCard = CardHelper.parseReadResponse(readResp.getData());
                logArea.append("==== THÔNG TIN KHÁCH HÀNG ====\n");
                logArea.append("ID: " + currentCard.userId + "\n");
                if (currentRole.equals("ADMIN")) {
                    try {
                        MembersDao dao = new MembersDao();
                        MemberRecord rec = dao.getByUserId(currentCard.userId);
                        if (rec != null) {
                            logArea.append("Họ Tên: " + (rec.fullName != null ? rec.fullName : "") + "\n");
                            logArea.append("Ngày Sinh: " + (rec.birthdate != null ? rec.birthdate : "") + "\n");
                            logArea.append("Hạn Tập: " + (rec.expiryDate != null ? rec.expiryDate : "") + "\n");
                            logArea.append("Số Dư: " + String.format("%,d VND", rec.balanceVnd) + "\n");
                        } else {
                            logArea.append("[DB] Không tìm thấy thành viên (ID=" + currentCard.userId + ")\n");
                        }
                    } catch (Exception dbEx) {
                        logArea.append("[DB] Lỗi đọc Database: " + dbEx.getMessage() + "\n");
                    }
                } else {
                    logArea.append("Họ Tên: [Mã hóa - cần PIN]\n");
                    logArea.append("Ngày Sinh: [Mã hóa - cần PIN]\n");
                    logArea.append("Số Dư: [Mã hóa - cần PIN]\n");
                    logArea.append("Hạn Tập: [Mã hóa - cần PIN]\n");
                }
                
                // Admin info
                if (currentRole.equals("ADMIN")) {
                    logArea.append("\nADMIN INFO:\n");
                    logArea.append("PIN Retry: " + currentCard.pinRetry + "/5\n");
                    String status = currentCard.pinRetry == 0 ? "LOCKED" : "ACTIVE";
                    logArea.append("Status: " + status + "\n");
                }

            } catch (Exception ex) {
                logArea.append("[LỖI] " + ex.getMessage() + "\n");
            }
        }).start();
    }

    private void createNewCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Họ tên (bắt buộc)
        JTextField nameField = new JTextField(20);
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Họ Tên (*):" ), gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        // Ngày sinh (DatePicker)
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Ngày Sinh (*):"), gbc);
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 31, 1));
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 12, 1));
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(2000, 1900, 2100, 1));
        // Tắt dấu phẩy phân cách hàng nghìn (1,000 -> 2000)
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);
        daySpinner.setPreferredSize(new Dimension(50, 25));
        monthSpinner.setPreferredSize(new Dimension(50, 25));
        yearSpinner.setPreferredSize(new Dimension(70, 25));
        datePanel.add(new JLabel("Ngày:"));
        datePanel.add(daySpinner);
        datePanel.add(new JLabel("Tháng:"));
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("Năm:"));
        datePanel.add(yearSpinner);
        gbc.gridx = 1;
        panel.add(datePanel, gbc);

        // CCCD (bắt buộc 12 số)
        JTextField cccdField = new JTextField(20);
        // Chỉ cho phép nhập số và tối đa 12 ký tự
        PlainDocument cccdDoc = (PlainDocument) cccdField.getDocument();
        cccdDoc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                    throws javax.swing.text.BadLocationException {
                if (string == null) return;
                String digits = string.replaceAll("\\D", "");
                int newLen = fb.getDocument().getLength() + digits.length();
                if (newLen <= 12) {
                    super.insertString(fb, offset, digits, attr);
                } else {
                    int allowed = 12 - fb.getDocument().getLength();
                    if (allowed > 0) super.insertString(fb, offset, digits.substring(0, Math.min(allowed, digits.length())), attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                    throws javax.swing.text.BadLocationException {
                String digits = text != null ? text.replaceAll("\\D", "") : "";
                int curLen = fb.getDocument().getLength();
                int newLen = curLen - length + digits.length();
                if (newLen <= 12) {
                    super.replace(fb, offset, length, digits, attrs);
                } else {
                    int allowed = 12 - (curLen - length);
                    if (allowed > 0) super.replace(fb, offset, length, digits.substring(0, Math.min(allowed, digits.length())), attrs);
                }
            }
        });
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("CCCD:"), gbc);
        gbc.gridx = 1;
        panel.add(cccdField, gbc);

        // Số dư (optional - mặc định 0)
        JTextField balanceField = new JTextField("0");
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Số Dư (VND):"), gbc);
        gbc.gridx = 1;
        panel.add(balanceField, gbc);

        // Hạn tập (optional - mặc định 0)
        JTextField expiryField = new JTextField("0");
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Hạn Tập (ngày):"), gbc);
        gbc.gridx = 1;
        panel.add(expiryField, gbc);

        // PIN (6 chữ số, mặc định 000000)
        JTextField pinField = new JTextField("000000");
        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(new JLabel("PIN (6 số):"), gbc);
        gbc.gridx = 1;
        panel.add(pinField, gbc);

        // Ảnh avatar (chọn file và xử lý)
        final byte[][] imageBytesHolder = new byte[1][];
        JButton imageBtn = new JButton("Chọn Ảnh (Avatar)");
        JLabel imageInfo = new JLabel("Chưa chọn ảnh");
        imageBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int res = chooser.showOpenDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File imgFile = chooser.getSelectedFile();
                try {
                    BufferedImage originalImage = ImageIO.read(imgFile);
                    Image scaled = originalImage.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
                    BufferedImage output = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
                    output.getGraphics().drawImage(scaled, 0, 0, null);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(output, "jpg", baos);
                    byte[] imageBytes = baos.toByteArray();
                    if (imageBytes.length > 4096) {
                        JOptionPane.showMessageDialog(this, "Ảnh quá lớn (>4KB)!");
                        return;
                    }
                    imageBytesHolder[0] = imageBytes;
                    imageInfo.setText("Đã chọn ảnh (" + imageBytes.length + " bytes)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi đọc/resize ảnh: " + ex.getMessage());
                }
            }
        });
        JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imagePanel.add(imageBtn);
        imagePanel.add(imageInfo);
        gbc.gridx = 0; gbc.gridy = 6;
        panel.add(new JLabel("Ảnh (64x64 JPG):"), gbc);
        gbc.gridx = 1;
        panel.add(imagePanel, gbc);

        int option = JOptionPane.showConfirmDialog(this, panel, "Tạo Thẻ Mới", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        try {
            // Kiểm tra họ tên
            String fullName = nameField.getText().trim();
            if (fullName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Họ tên không được để trống!");
                return;
            }

            // Kiểm tra PIN
            String pinStr = pinField.getText().trim();
            if (!pinStr.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(this, "❌ PIN phải là 6 chữ số!");
                return;
            }

            CardData newCard = new CardData();
            // ID tự động (random hoặc tăng dần)
            newCard.userId = (int) (Math.random() * 65535) + 1;
            newCard.fullName = fullName;
            // Validate CCCD bắt buộc 12 số
            String cccdStr = cccdField.getText().trim();
            if (!cccdStr.matches("\\d{12}")) {
                JOptionPane.showMessageDialog(this, "❌ CCCD phải gồm đúng 12 chữ số!");
                return;
            }
            newCard.cccd = cccdStr;
            newCard.balance = Integer.parseInt(balanceField.getText());
            newCard.expiryDays = (short) Integer.parseInt(expiryField.getText());
            
            // DOB
            newCard.dobDay = (byte) ((Integer) daySpinner.getValue()).intValue();
            newCard.dobMonth = (byte) ((Integer) monthSpinner.getValue()).intValue();
            newCard.dobYear = (short) ((Integer) yearSpinner.getValue()).intValue();
            
            // PIN
            newCard.pin = pinStr; // Use full 6-digit string
            newCard.pinRetry = 5; // Default 5 attempts

            // Validate CCCD uniqueness before proceeding
            if (newCard.cccd != null && !newCard.cccd.isEmpty()) {
                MembersDao dao = new MembersDao();
                if (dao.existsCccd(newCard.cccd)) {
                    JOptionPane.showMessageDialog(this,
                        "❌ CCCD đã tồn tại trong Database. Vui lòng nhập CCCD khác.",
                        "Trùng CCCD",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            logArea.append("\n[BƯỚC 1] Kết nối thẻ...\n");
            pcsc.connectFirstPresentOrFirst();
            logArea.append("[OK] Kết nối thành công!\n");

            // Select applet
            logArea.append("[BƯỚC 2] Chọn applet...\n");
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không chọn được applet (SW: " + 
                             Integer.toHexString(selectResp.getSW()).toUpperCase() + ")\n");
                return;
            }
            logArea.append("[OK] Applet đã sẵn sàng!\n");

            // Check if card is blank by reading
            logArea.append("[BƯỚC 2.5] Kiểm tra trạng thái thẻ...\n");
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            
            if ((readResp.getSW() & 0xFF00) == 0x9000) {
                byte[] data = readResp.getData();
                int existingUserId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                
                if (existingUserId != 0) {
                    logArea.append("[CẢNH BÁO] Thẻ đã có dữ liệu (UserID: " + existingUserId + ")\n");
                    logArea.append("[BƯỚC 2.6] Xóa dữ liệu cũ (reset thẻ)...\n");
                    
                    // Write blank data (UserID=0) to reset card - 96 bytes layout (CBC with IV)
                    byte[] blankData = new byte[96];
                    blankData[66] = 5; // Reset PIN retry to 5 (offset 66 in CBC layout)
                    
                    javax.smartcardio.CommandAPDU deleteCmd = 
                        new javax.smartcardio.CommandAPDU(0x00, 0xD0, 0x00, 0x00, blankData);
                    javax.smartcardio.ResponseAPDU deleteResp = pcsc.transmit(deleteCmd);
                    
                    if ((deleteResp.getSW() & 0xFF00) != 0x9000) {
                        logArea.append("[LỖI] Không thể xóa dữ liệu cũ (SW: " + 
                                     Integer.toHexString(deleteResp.getSW()).toUpperCase() + ")\n");
                        return;
                    }
                    logArea.append("[OK] Đã xóa dữ liệu cũ, thẻ đã trống!\n");
                } else {
                    logArea.append("[OK] Thẻ đang trống, sẵn sàng ghi mới\n");
                }
            }

            // Write card data
            logArea.append("[BƯỚC 3] Ghi dữ liệu vào thẻ...\n");
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(newCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);

            if ((writeResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Ghi thẻ thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
                return;
            }
            
            logArea.append("[OK] Ghi thẻ thành công!\n\n");
            logArea.append("════════════════════════════\n");
            logArea.append("    ✅ TẠO THẺ THÀNH CÔNG\n");
            logArea.append("════════════════════════════\n");
            logArea.append(formatCardInfo(newCard));
            logArea.append("PIN: " + pinStr + "\n");
            logArea.append("════════════════════════════\n");

            // Persist to Database FIRST (so record exists for RSA update)
            try {
                MembersDao dao = new MembersDao();
                MemberRecord rec = new MemberRecord();
                rec.id = newCard.userId;
                rec.fullName = newCard.fullName;
                rec.balanceVnd = newCard.balance;
                java.time.LocalDate bd = null;
                if (newCard.dobYear > 0 && newCard.dobMonth > 0 && newCard.dobDay > 0) {
                    bd = java.time.LocalDate.of(newCard.dobYear, newCard.dobMonth, newCard.dobDay);
                }
                rec.birthdate = bd;
                java.time.LocalDate expiry = (newCard.expiryDays > 0)
                    ? java.time.LocalDate.now().plusDays(newCard.expiryDays)
                    : null;
                rec.expiryDate = expiry;
                rec.cardUid = null; // Not available from reader
                rec.pinretry = newCard.pinRetry;
                rec.transactionHistory = null;
                rec.cccd = cccdField.getText().trim();
                rec.avatarData = imageBytesHolder[0];
                dao.upsert(rec);
                logArea.append("[DB] Đã lưu thành viên vào Database (ID=" + rec.id + ")\n");
            } catch (Exception dbEx) {
                logArea.append("[DB] Lỗi lưu Database: " + dbEx.getMessage() + "\n");
            }

            // ✅ Đọc RSA public key từ thẻ và lưu vào DB (AFTER upsert so record exists)
            logArea.append("[BƯỚC 3.5] Đọc RSA Public Key từ thẻ...\n");
            try {
                RsaKeyService.registerCardPublicKey(pcsc, newCard.userId);
                logArea.append("[OK] Đã lưu RSA Public Key vào Database!\n");
            } catch (Exception rsaEx) {
                logArea.append("[CẢNH BÁO] Không lấy được RSA: " + rsaEx.getMessage() + "\n");
            }

            // Gửi ảnh xuống thẻ theo chunks nếu có (thẻ sẽ mã hóa lưu trữ)
            if (imageBytesHolder[0] != null && imageBytesHolder[0].length > 0) {
                try {
                    logArea.append("[BƯỚC 4] Gửi avatar xuống thẻ (" + imageBytesHolder[0].length + " bytes)...\n");
                    // Clear trước
                    pcsc.transmit(CardHelper.buildAvatarClearCommand());
                    int offset = 0;
                    int chunkSize = 200;
                    while (offset < imageBytesHolder[0].length) {
                        int len = Math.min(chunkSize, imageBytesHolder[0].length - offset);
                        byte[] chunk = new byte[len];
                        System.arraycopy(imageBytesHolder[0], offset, chunk, 0, len);
                        javax.smartcardio.ResponseAPDU resp = pcsc.transmit(CardHelper.buildAvatarWriteChunk(offset, chunk));
                        if ((resp.getSW() & 0xFF00) != 0x9000) {
                            logArea.append("[LỖI] Ghi chunk avatar thất bại tại offset " + offset + " (SW=" + Integer.toHexString(resp.getSW()) + ")\n");
                            break;
                        }
                        offset += len;
                    }
                    logArea.append("[OK] Đã gửi avatar với " + offset + " bytes\n");
                } catch (Exception ex) {
                    logArea.append("[LỖI] Gửi avatar thất bại: " + ex.getMessage() + "\n");
                }
            }
            
            JOptionPane.showMessageDialog(this, 
                "Tạo thẻ thành công!\n\n" +
                "Họ Tên: " + newCard.fullName + "\n" +
                "ID: " + newCard.userId + "\n" +
                "PIN: " + pinStr,
                "Thành Công",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception ex) {
            logArea.append("[LỖI NGHIÊM TRỌNG] " + ex.getMessage() + "\n");
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "❌ Lỗi: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Helper method to create modern styled buttons for admin panel
     */
    private JButton createModernButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(brighten(bgColor, 20));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgColor);
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
    
    /**
     * Display card info when customer swipes (real-time sync)
     * Shows decrypted card data after PIN verification
     */
    private void displayCardInfo(CardData card) {
        logArea.setText("");
        logArea.append("═══ KHÁCH HÀNG ĐÃ XÁC THỰC ═══\n\n");
        
        // Lấy thông tin từ DB để bổ sung (CCCD, RSA, etc.)
        MemberRecord rec = null;
        try {
            MembersDao dao = new MembersDao();
            rec = dao.getByUserId(card.userId);
        } catch (Exception ignored) {}
        
        // ✅ Hiển thị thông tin từ card (đã decrypt sau verify PIN)
        logArea.append("📇 THÔNG TIN TỪ THẺ:\n");
        logArea.append("ID: " + card.userId + "\n");
        logArea.append("Họ Tên: " + (card.fullName != null && !card.fullName.isEmpty() ? card.fullName : "[Không có]") + "\n");
        logArea.append("Ngày Sinh: " + (card.dobYear > 0 ? card.getDobString() : "[Không có]") + "\n");
        // CCCD: ưu tiên từ card, fallback từ DB
        String cccdDisplay = "[Không có]";
        if (card.cccd != null && !card.cccd.isEmpty()) {
            cccdDisplay = card.cccd;
        } else if (rec != null && rec.cccd != null && !rec.cccd.isEmpty()) {
            cccdDisplay = rec.cccd + " (DB)";
        }
        logArea.append("CCCD: " + cccdDisplay + "\n");
        logArea.append("Số Dư: " + (card.balance >= 0 ? String.format("%,d VND", card.balance) : "[Mã hóa]") + "\n");
        logArea.append("Hạn Tập: " + (card.expiryDays >= 0 ? card.expiryDays + " ngày" : "[Mã hóa]") + "\n");
        logArea.append("PIN Retry: " + card.pinRetry + "/5\n");
        logArea.append("Trạng thái: " + (card.isLocked() ? "🔒 Đã khóa" : "✅ Hoạt động") + "\n");
        
        // ✅ Bổ sung thông tin từ DB (RSA, timestamps, v.v.)
        if (rec != null) {
            logArea.append("\n📊 THÔNG TIN TỪ DATABASE:\n");
            // RSA key presence
            boolean hasRsa = (rec.rsaModulusHex != null && !rec.rsaModulusHex.isEmpty()) &&
                             (rec.rsaExponentHex != null && !rec.rsaExponentHex.isEmpty());
            logArea.append("RSA: " + (hasRsa ? "✅ Có" : "❌ Không") + "\n");
            // Last check-in
            logArea.append("Check-in gần nhất: " + (rec.lastCheckinDate != null ? rec.lastCheckinDate : "Chưa") + "\n");
            // Transaction history (summary)
            if (rec.transactionHistory != null && !rec.transactionHistory.isEmpty()) {
                logArea.append("Giao Dịch: " + rec.transactionHistory + "\n");
            }
            // Created/Updated timestamps
            logArea.append("Tạo lúc: " + (rec.createdAt != null ? rec.createdAt : "") + "\n");
            logArea.append("Cập nhật: " + (rec.updatedAt != null ? rec.updatedAt : "") + "\n");
        } else {
            logArea.append("\n[DB] Không tìm thấy trong Database\n");
        }
        
        // Cảnh báo hết hạn
        if (card.expiryDays == 0) {
            logArea.append("\n⚠️ THẺ HẾT HẠN!\n");
        } else if (card.expiryDays > 0 && card.expiryDays <= 7) {
            logArea.append("\n⚠️ THẺ SẮP HẾT HẠN!\n");
        }
    }
    
    /**
     * Format card info as string (reusable helper)
     */
    private String formatCardInfo(CardData card) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(card.userId).append("\n");
        if (card.fullName != null && !card.fullName.isEmpty()) {
            sb.append("Họ Tên: ").append(card.fullName).append("\n");
        } else {
            sb.append("Họ Tên: [Mã hóa - cần PIN]\n");
        }
        if (card.dobYear > 0) {
            sb.append("Ngày Sinh: ").append(card.getDobString()).append("\n");
        } else {
            sb.append("Ngày Sinh: [Mã hóa - cần PIN]\n");
        }
        if (card.balance == -1) {
            sb.append("Số Dư: [Mã hóa - cần PIN]\n");
        } else {
            sb.append("Số Dư: ").append(String.format("%,d", card.balance)).append(" VND\n");
        }
        if (card.expiryDays == -1) {
            sb.append("Hạn Tập: [Mã hóa - cần PIN]\n");
        } else {
            sb.append("Hạn Tập: ").append(card.expiryDays).append(" ngày\n");
        }
        return sb.toString();
    }
    
    /**
     * Show purchase approval dialog (called from customer window)
     */
    private boolean showPurchaseApprovalDialog(List<CardEventBroadcaster.CartItem> items, int totalPrice) {
        StringBuilder message = new StringBuilder();
        message.append("YÊU CẦU MUA HÀNG TỪ KHÁCH:\n\n");
        for (CardEventBroadcaster.CartItem item : items) {
            message.append("• ").append(item.item.name)
                   .append(" x").append(item.quantity)
                   .append(" = ").append(item.item.price * item.quantity).append(" VND\n");
        }
        message.append("\nTổng cộng: ").append(totalPrice).append(" VND\n\n");
        message.append("Kiểm tra kho và xác nhận?");
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message.toString(),
            "Xác Nhận Mua Hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + " Đơn hàng " + totalPrice + " VND\n");
        
        return approved;
    }
    
    private boolean showTopupApprovalDialog(int amount, String paymentMethod) {
        String icon = paymentMethod.contains("QR") ? "📱" : "💵";
        String message = "YÊU CẦU NẠP TIỀN TỪ KHÁCH:\n\n" +
                        icon + " Phương thức: " + paymentMethod + "\n" +
                        "Số tiền: " + String.format("%,d VND", amount) + "\n\n" +
                        "Xác nhận đã nhận tiền?";
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message,
            "Xác Nhận Nạp Tiền",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + 
                      " Nạp " + String.format("%,d VND", amount) + " (" + paymentMethod + ")\n");
        
        return approved;
    }
    
    /**
     * Delete card - Admin only
     */
    private void deleteCard() {
        logArea.setText("");
        logArea.append("[ADMIN] Xóa thẻ người dùng\n\n");
        
        try {
            pcsc.connectFirstPresentOrFirst();
            logArea.append("[OK] Kết nối thẻ thành công!\n\n");
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Read current data first
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Đọc thẻ thất bại\n");
                return;
            }
            
            CardData card = CardHelper.parseReadResponse(readResp.getData());
            
            if (card.userId == 0) {
                logArea.append("[THÔNG BÁO] Thẻ đã trống, không cần xóa\n");
                return;
            }
            
            // Lấy thông tin từ Database để hiển thị
            MembersDao dao = new MembersDao();
            MemberRecord dbRecord = dao.getByUserId(card.userId);
            
            String memberInfo;
            if (dbRecord != null) {
                memberInfo = "ID: " + dbRecord.id + "\n" +
                            "Họ Tên: " + (dbRecord.fullName != null ? dbRecord.fullName : "[Không có]") + "\n" +
                            "Ngày Sinh: " + (dbRecord.birthdate != null ? dbRecord.birthdate : "[Không có]") + "\n" +
                            "Số Dư: " + String.format("%,d", dbRecord.balanceVnd) + " VND\n" +
                            "Hạn Tập: " + (dbRecord.expiryDate != null ? dbRecord.expiryDate : "[Không có]") + "\n" +
                            "CCCD: " + (dbRecord.cccd != null ? dbRecord.cccd : "[Không có]");
            } else {
                memberInfo = "ID: " + card.userId + "\n" +
                            "[Không tìm thấy trong Database]";
            }
            
            // Confirm deletion
            String confirmMsg = "XÓA THẺ NGƯỜI DÙNG?\n\n" +
                              memberInfo + "\n\n" +
                              "⚠️ Hành động này sẽ XÓA CẢ DỮ LIỆU TRÊN DATABASE!\n" +
                              "KHÔNG THỂ HOÀN TÁC!";
            
            int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmMsg,
                "Xác Nhận Xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm != JOptionPane.YES_OPTION) {
                logArea.append("[HỦY] Không xóa thẻ\n");
                return;
            }
            
            // Reset card to zero values
            CardData emptyCard = new CardData();
            emptyCard.userId = 0;
            emptyCard.balance = 0;
            emptyCard.expiryDays = 0;
            emptyCard.pin = "000000"; 
            emptyCard.pinRetry = 5;
            emptyCard.fullName = "";
            emptyCard.dobDay = 0;
            emptyCard.dobMonth = 0;
            emptyCard.dobYear = 0;
            
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(emptyCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            
            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                // Xóa dữ liệu trên Database
                try {
                    dao.deleteMember(card.userId);
                    logArea.append("[THÀNH CÔNG] Đã xóa thẻ và dữ liệu Database:\n");
                } catch (Exception dbEx) {
                    logArea.append("[CẢNH BÁO] Thẻ đã xóa nhưng lỗi xóa DB: " + dbEx.getMessage() + "\n");
                }
                
                if (dbRecord != null) {
                    logArea.append(" ID: " + dbRecord.id + "\n");
                    logArea.append(" Họ Tên: " + (dbRecord.fullName != null ? dbRecord.fullName : "[Không có]") + "\n");
                    logArea.append(" CCCD: " + (dbRecord.cccd != null ? dbRecord.cccd : "[Không có]") + "\n");
                } else {
                    logArea.append(" ID: " + card.userId + "\n");
                }
                logArea.append("Thẻ đã được reset và dữ liệu đã xóa khỏi hệ thống\n");
                
                JOptionPane.showMessageDialog(this, 
                    "Xóa thẻ thành công!\nThẻ đã được reset và dữ liệu đã xóa khỏi Database.",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                logArea.append("[LỖI] Xóa thẻ thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Unlock card - Admin only (reset retry counter without changing PIN)
     */
    private void unlockCard() {
        if (currentCard == null || currentCard.userId == 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        logArea.setText("");
        logArea.append("[ADMIN] Mở khóa thẻ #" + currentCard.userId + "\n\n");
        
        if (currentCard.pinRetry >= 5) {
            JOptionPane.showMessageDialog(this, "Thẻ chưa bị khóa (Retry: " + currentCard.pinRetry + "/5)", 
                "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Mở khóa thẻ cho: " + currentCard.fullName + "\n\n" +
            "Retry hiện tại: " + currentCard.pinRetry + "/5\n" +
            "Sẽ reset về: 5/5\n\n" +
            "Xác nhận mở khóa?",
            "Xác Nhận Mở Khóa",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
            
        if (confirm != JOptionPane.YES_OPTION) {
            logArea.append("[HỦY] Không mở khóa\n");
            return;
        }
        
        try {
            pcsc.connectFirstPresentOrFirst();
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Use admin unlock command (no PIN required)
            logArea.append("[BƯỚC 1] Gửi lệnh admin unlock...\n");
            javax.smartcardio.CommandAPDU unlockCmd = CardHelper.buildAdminUnlockCommand();
            javax.smartcardio.ResponseAPDU unlockResp = pcsc.transmit(unlockCmd);
            
            if ((unlockResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[✅ THÀNH CÔNG] Đã mở khóa thẻ!\n");
                logArea.append("Retry counter: 5/5\n");
                
                JOptionPane.showMessageDialog(this, 
                    "✅ Mở khóa thành công!\n\n" +
                    "Retry counter đã reset về 5/5",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
                    
                currentCard.pinRetry = 5;
            } else {
                logArea.append("[LỖI] Mở khóa thất bại (SW: " + 
                             Integer.toHexString(unlockResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Reset PIN - Admin only (requires old PIN to re-encrypt balance/expiry)
     */
    private void resetPin() {
        if (currentCard == null || currentCard.userId == 0) {
            JOptionPane.showMessageDialog(this, "❌ Vui lòng quẹt thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        logArea.setText("");
        logArea.append("[ADMIN] Reset PIN cho thẻ #" + currentCard.userId + "\n\n");

        // 1) Lấy dữ liệu Backup từ Database trước
        MemberRecord dbRecord = null;
        try {
            MembersDao dao = new MembersDao();
            dbRecord = dao.getByUserId(currentCard.userId);
            if (dbRecord == null) {
                JOptionPane.showMessageDialog(this, "❌ Không tìm thấy dữ liệu gốc trong Database! Không thể reset thẻ.", "Lỗi DB", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "❌ Lỗi kết nối Database: " + ex.getMessage());
            return;
        }

        String newPin = JOptionPane.showInputDialog(this, "Nhập PIN mới (6 số):");
        if (newPin == null || !newPin.matches("\\d{6}")) return;

        try {
            pcsc.connectFirstPresentOrFirst();

            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }

            // B1: Cập nhật PIN mới xuống thẻ
            logArea.append("[B1] Cập nhật PIN mới xuống thẻ...\n");
            javax.smartcardio.CommandAPDU resetCmd = CardHelper.buildAdminResetPinCommand(newPin);
            javax.smartcardio.ResponseAPDU resetResp = pcsc.transmit(resetCmd);
            if ((resetResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Lệnh Reset PIN thất bại\n");
                return;
            }

            // B2: Xác thực bằng PIN mới (để mở khóa quyền Ghi)
            logArea.append("[B2] Xác thực bằng PIN mới...\n");
            javax.smartcardio.CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand(newPin);
            javax.smartcardio.ResponseAPDU verifyResp = pcsc.transmit(verifyCmd);
            if ((verifyResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Verify PIN mới thất bại\n");
                // Cập nhật retry trong DB nếu có mã 63Cx hoặc 6983
                try {
                    short retries;
                    if (verifyResp.getSW() == 0x6983) retries = 0; else if ((verifyResp.getSW() & 0xFFF0) == 0x63C0) retries = (short)(verifyResp.getSW() & 0xF); else retries = -1;
                    if (retries >= 0) new MembersDao().updatePinRetry(currentCard.userId, retries);
                } catch (Exception ignored) {}
                return;
            }

            // Verify thành công: sync retry=5 vào DB trước khi ghi
            try { new MembersDao().updatePinRetry(currentCard.userId, (short)5); } catch (Exception ignored) {}

            // B3: Chuẩn bị dữ liệu từ Database
            logArea.append("[B3] Đồng bộ dữ liệu từ Database...\n");
            CardData dataToRestore = new CardData();
            dataToRestore.userId = dbRecord.id;
            dataToRestore.fullName = dbRecord.fullName;
            dataToRestore.balance = dbRecord.balanceVnd;
            if (dbRecord.expiryDate != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(),
                    dbRecord.expiryDate
                );
                dataToRestore.expiryDays = (short) Math.max(0, daysBetween);
            } else {
                dataToRestore.expiryDays = 0;
            }
            if (dbRecord.birthdate != null) {
                dataToRestore.dobDay = (byte) dbRecord.birthdate.getDayOfMonth();
                dataToRestore.dobMonth = (byte) dbRecord.birthdate.getMonthValue();
                dataToRestore.dobYear = (short) dbRecord.birthdate.getYear();
            }
            dataToRestore.pin = newPin;
            dataToRestore.pinRetry = 5;

            // B4: Ghi đè dữ liệu xuống thẻ
            logArea.append("[B4] Ghi đè dữ liệu đã mã hóa...\n");
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(dataToRestore);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                JOptionPane.showMessageDialog(this, "✅ Reset PIN & Khôi phục dữ liệu thành công!");
                currentCard = dataToRestore;
                logArea.append("HOÀN TẤT.\n");
            } else {
                JOptionPane.showMessageDialog(this, "❌ Lỗi khi ghi dữ liệu!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sửa thông tin thành viên - Admin only
     * Flow đơn giản: Quẹt thẻ → User nhập PIN → Admin sửa → Ghi thẻ + DB
     */
    private void editMemberInfo() {
        logArea.setText("");
        logArea.append("[ADMIN] SỬA THÔNG TIN THÀNH VIÊN\n");
        logArea.append("════════════════════════════════\n\n");
        
        try {
            // B1: Quẹt thẻ
            logArea.append("[B1] Đang kết nối thẻ...\n");
            pcsc.connectFirstPresentOrFirst();
            
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không đọc được thẻ\n");
                return;
            }
            CardData cardData = CardHelper.parseReadResponse(readResp.getData());
            logArea.append("[OK] Đọc thẻ ID=" + cardData.userId + "\n");
            
            // B2: User nhập PIN
            JPasswordField pinField = new JPasswordField();
            int pinOpt = JOptionPane.showConfirmDialog(this, 
                new Object[]{"Nhập PIN của thẻ (6 số):", pinField}, 
                "Xác thực PIN", JOptionPane.OK_CANCEL_OPTION);
            if (pinOpt != JOptionPane.OK_OPTION) return;
            
            String pin = new String(pinField.getPassword());
            if (!pin.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(this, "❌ PIN phải là 6 chữ số!");
                return;
            }
            
            logArea.append("[B2] Xác thực PIN...\n");
            javax.smartcardio.CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand(pin);
            javax.smartcardio.ResponseAPDU verifyResp = pcsc.transmit(verifyCmd);
            if ((verifyResp.getSW() & 0xFF00) != 0x9000) {
                String status = CardHelper.parsePinStatus(verifyResp.getSW());
                JOptionPane.showMessageDialog(this, "❌ " + status);
                logArea.append("[LỖI] " + status + "\n");
                return;
            }
            logArea.append("[OK] PIN chính xác!\n");
            
            // Parse decrypted data từ thẻ
            CardData decrypted = CryptoHelper.parseDecryptedCardData(verifyResp.getData(), pin);
            decrypted.userId = cardData.userId;
            
            // B3: Hiển thị form sửa với dữ liệu từ thẻ
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            JTextField nameField = new JTextField(decrypted.fullName != null ? decrypted.fullName : "", 20);
            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new JLabel("Họ Tên:"), gbc);
            gbc.gridx = 1;
            panel.add(nameField, gbc);
            
            gbc.gridx = 0; gbc.gridy = 1;
            panel.add(new JLabel("Ngày Sinh:"), gbc);
            JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(decrypted.dobDay > 0 ? decrypted.dobDay : 1, 1, 31, 1));
            JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(decrypted.dobMonth > 0 ? decrypted.dobMonth : 1, 1, 12, 1));
            JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(decrypted.dobYear > 0 ? decrypted.dobYear : 2000, 1900, 2100, 1));
            // Tắt dấu phẩy phân cách hàng nghìn (1,000 -> 2000)
            JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
            yearSpinner.setEditor(yearEditor);
            daySpinner.setPreferredSize(new Dimension(50, 25));
            monthSpinner.setPreferredSize(new Dimension(50, 25));
            yearSpinner.setPreferredSize(new Dimension(70, 25));
            datePanel.add(daySpinner);
            datePanel.add(new JLabel("/"));
            datePanel.add(monthSpinner);
            datePanel.add(new JLabel("/"));
            datePanel.add(yearSpinner);
            gbc.gridx = 1;
            panel.add(datePanel, gbc);
            
            JTextField cccdField = new JTextField(decrypted.cccd != null ? decrypted.cccd : "", 20);
            // Filter: chỉ số và tối đa 12 ký tự
            PlainDocument cccdDoc = (PlainDocument) cccdField.getDocument();
            cccdDoc.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                        throws javax.swing.text.BadLocationException {
                    if (string == null) return;
                    String digits = string.replaceAll("\\D", "");
                    int newLen = fb.getDocument().getLength() + digits.length();
                    if (newLen <= 12) {
                        super.insertString(fb, offset, digits, attr);
                    } else {
                        int allowed = 12 - fb.getDocument().getLength();
                        if (allowed > 0) super.insertString(fb, offset, digits.substring(0, Math.min(allowed, digits.length())), attr);
                    }
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                        throws javax.swing.text.BadLocationException {
                    String digits = text != null ? text.replaceAll("\\D", "") : "";
                    int curLen = fb.getDocument().getLength();
                    int newLen = curLen - length + digits.length();
                    if (newLen <= 12) {
                        super.replace(fb, offset, length, digits, attrs);
                    } else {
                        int allowed = 12 - (curLen - length);
                        if (allowed > 0) super.replace(fb, offset, length, digits.substring(0, Math.min(allowed, digits.length())), attrs);
                    }
                }
            });
            gbc.gridx = 0; gbc.gridy = 2;
            panel.add(new JLabel("CCCD:"), gbc);
            gbc.gridx = 1;
            panel.add(cccdField, gbc);
            
            JTextField balanceField = new JTextField(String.valueOf(decrypted.balance), 20);
            gbc.gridx = 0; gbc.gridy = 3;
            panel.add(new JLabel("Số Dư (VND):"), gbc);
            gbc.gridx = 1;
            panel.add(balanceField, gbc);
            
            JTextField expiryField = new JTextField(String.valueOf(decrypted.expiryDays), 20);
            gbc.gridx = 0; gbc.gridy = 4;
            panel.add(new JLabel("Hạn Tập (ngày):"), gbc);
            gbc.gridx = 1;
            panel.add(expiryField, gbc);
            
            int option = JOptionPane.showConfirmDialog(this, panel, "Sửa Thông Tin - ID: " + decrypted.userId, 
                                                       JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                logArea.append("[HỦY]\n");
                return;
            }
            
            // Parse input
            String newName = nameField.getText().trim();
            String newCccd = cccdField.getText().trim();
            // Bắt buộc CCCD đúng 12 số
            if (!newCccd.matches("\\d{12}")) {
                JOptionPane.showMessageDialog(this,
                    "❌ CCCD phải gồm đúng 12 chữ số!",
                    "CCCD không hợp lệ",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            int newBalance = Integer.parseInt(balanceField.getText().trim());
            short newExpiry = (short) Integer.parseInt(expiryField.getText().trim());
            byte newDobDay = (byte) ((Integer) daySpinner.getValue()).intValue();
            byte newDobMonth = (byte) ((Integer) monthSpinner.getValue()).intValue();
            short newDobYear = (short) ((Integer) yearSpinner.getValue()).intValue();

            // Validate CCCD uniqueness (exclude current user ID)
            if (newCccd != null && !newCccd.isEmpty()) {
                MembersDao dao = new MembersDao();
                if (dao.existsCccdExceptId(newCccd, decrypted.userId)) {
                    JOptionPane.showMessageDialog(this,
                        "❌ CCCD đã tồn tại cho thành viên khác. Vui lòng nhập CCCD khác.",
                        "Trùng CCCD",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            
            // B4: Ghi xuống thẻ
            logArea.append("[B3] Ghi dữ liệu mới xuống thẻ...\n");
            CardData newData = new CardData();
            newData.userId = decrypted.userId;
            newData.fullName = newName;
            newData.balance = newBalance;
            newData.expiryDays = newExpiry;
            newData.dobDay = newDobDay;
            newData.dobMonth = newDobMonth;
            newData.dobYear = newDobYear;
            newData.cccd = newCccd;
            newData.pin = pin;
            newData.pinRetry = 5;
            
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(newData);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            if ((writeResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Ghi thẻ thất bại\n");
                return;
            }
            logArea.append("[OK] Ghi thẻ thành công!\n");
            
            // B5: Cập nhật DB
            logArea.append("[B4] Cập nhật Database...\n");
            MembersDao dao = new MembersDao();
            MemberRecord record = dao.getByUserId(decrypted.userId);
            if (record == null) {
                record = new MemberRecord();
                record.id = decrypted.userId;
            }
            record.fullName = newName;
            record.balanceVnd = newBalance;
            record.birthdate = java.time.LocalDate.of(newDobYear, newDobMonth, newDobDay);
            record.expiryDate = java.time.LocalDate.now().plusDays(newExpiry);
            record.cccd = newCccd;
            dao.upsert(record);
            logArea.append("[OK] Cập nhật DB thành công!\n");
            
            logArea.append("\n✅ SỬA THÔNG TIN THÀNH CÔNG!\n");
            JOptionPane.showMessageDialog(this, "✅ Sửa thông tin thành công!");
            
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "❌ Số dư hoặc hạn tập không hợp lệ!");
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
}