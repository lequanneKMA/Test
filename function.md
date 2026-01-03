# Chức năng theo vai trò (Admin / Nhân viên / Khách hàng)

Tài liệu này tổng hợp, theo luồng thực tế trong mã nguồn, toàn bộ các chức năng dành cho từng vai trò: Admin, Nhân viên (Staff/Employee), và Khách hàng.

## Tổng Quan Vai Trò
- Admin và Nhân viên dùng cửa sổ quản trị ở [src/StaffWindow.java](src/StaffWindow.java#L12) với panel chọn vai trò [src/RoleSelectionPanel.java](src/RoleSelectionPanel.java#L7).
- Khách hàng dùng cửa sổ tự phục vụ ở [src/CustomerWindow.java](src/CustomerWindow.java#L23).
- Đồng bộ thông tin, yêu cầu phê duyệt giữa hai cửa sổ thông qua broadcaster [src/CardEventBroadcaster.java](src/CardEventBroadcaster.java#L6).

## Admin
- **Chọn vai trò Admin**: Yêu cầu mật khẩu "admin123@" tại [src/RoleSelectionPanel.java](src/RoleSelectionPanel.java#L62-L77). Khi hợp lệ, Staff UI chuyển sang chế độ Admin tại [src/StaffWindow.java](src/StaffWindow.java#L57-L58).
- **Quẹt thẻ và xem thông tin**: Hiển thị thông tin người dùng, đọc thêm từ DB nếu có tại [src/FunctionPanel.java](src/FunctionPanel.java#L146-L174) và [src/FunctionPanel.java](src/FunctionPanel.java#L200-L230).
- **Tạo thẻ mới**: Thu thập thông tin (họ tên, DOB, CCCD, số dư, hạn tập, PIN, avatar), ghi xuống thẻ, lưu DB, đăng ký RSA public key, và gửi avatar theo chunks:
  - Form nhập + kiểm tra đầu vào: [src/FunctionPanel.java](src/FunctionPanel.java#L212-L314)
  - Ghi dữ liệu thẻ (`WRITE`): [src/FunctionPanel.java](src/FunctionPanel.java#L361-L395)
  - Lưu DB (`MembersDao.upsert`): [src/FunctionPanel.java](src/FunctionPanel.java#L422-L441)
  - Đọc và lưu RSA từ thẻ: [src/FunctionPanel.java](src/FunctionPanel.java#L449-L456) với helper [src/RsaKeyService.java](src/RsaKeyService.java#L41)
  - Gửi avatar xuống thẻ (`AVATAR_CLEAR`/`AVATAR_WRITE`): [src/FunctionPanel.java](src/FunctionPanel.java#L461-L479)
- **Xóa thẻ**: Reset dữ liệu thẻ về mặc định, xác nhận người dùng trước khi xóa tại [src/FunctionPanel.java](src/FunctionPanel.java#L602-L701).
- **Mở khóa thẻ (reset retry counter)**: Không cần PIN, gửi lệnh admin unlock, đặt `pinRetry` về 5 tại [src/FunctionPanel.java](src/FunctionPanel.java#L706-L786).
- **Reset PIN (Admin)**: Đặt PIN mới (không cần PIN cũ), verify PIN mới, rồi khôi phục dữ liệu từ DB và ghi lại xuống thẻ tại [src/FunctionPanel.java](src/FunctionPanel.java#L792-L923).
- **Sửa thông tin thành viên**: Quẹt thẻ, người dùng nhập PIN để giải mã, admin chỉnh sửa và ghi lại cả thẻ + DB tại [src/FunctionPanel.java](src/FunctionPanel.java#L934-L1104).
- **Xem danh sách thành viên, giao dịch**: Mở bảng dữ liệu và lịch sử giao dịch tại [src/MembersTableDialog.java](src/MembersTableDialog.java#L1-L120) và [src/TransactionsTableDialog.java](src/TransactionsTableDialog.java#L19).

## Nhân Viên (Employee/Staff)
- **Chọn vai trò Nhân viên**: Mặc định là EMPLOYEE; có thể được đặt lại sau khi nhập Admin sai tại [src/RoleSelectionPanel.java](src/RoleSelectionPanel.java#L85-L90).
- **Quẹt thẻ khách**: Hiển thị thông tin cơ bản từ thẻ, các trường nhạy cảm hiển thị "[Mã hóa - cần PIN]" tại [src/FunctionPanel.java](src/FunctionPanel.java#L126-L174;L200-L230).
- **Phê duyệt mua hàng**: Nhận yêu cầu từ cửa sổ khách, xác nhận/ từ chối, ghi log hiển thị tại [src/FunctionPanel.java](src/FunctionPanel.java#L624-L652).
- **Phê duyệt nạp tiền**: Nhận yêu cầu từ cửa sổ khách, xác nhận/ từ chối, ghi log hiển thị tại [src/FunctionPanel.java](src/FunctionPanel.java#L652-L688).
- **Nhận broadcast sự kiện**: Khi khách đã verify PIN, dữ liệu giải mã hiển thị đồng bộ tại [src/FunctionPanel.java](src/FunctionPanel.java#L520-L588).

## Khách Hàng (Customer)
- **Quẹt thẻ + Verify PIN**: Kết nối thẻ, `SELECT` applet, `READ` dữ liệu, sau đó người dùng nhập PIN để gửi `VERIFY_PIN`. Nếu đúng, hiển thị đầy đủ thông tin và bật các thao tác tại [src/CustomerWindow.java](src/CustomerWindow.java#L246-L314;L320-L352).
- **Xem thông tin cá nhân**: Hiển thị thông tin đã giải mã, kèm avatar từ DB nếu có tại [src/CustomerWindow.java](src/CustomerWindow.java#L374-L433).
- **Check-in**: Trừ 1 ngày sử dụng trên thẻ (sau verify PIN), cập nhật `expiryDays` trên thẻ và DB tại [src/CustomerWindow.java](src/CustomerWindow.java#L354-L420).
- **Gia hạn gói tập**: Trừ tiền từ số dư, cộng ngày, ghi thẻ và log DB; rollback nếu lỗi tại [src/CustomerWindow.java](src/CustomerWindow.java#L435-L570).
- **Đổi PIN (người dùng)**: Verify PIN cũ, gọi `CHANGE_PIN`, sau đó đọc lại/tháo gỡ dữ liệu với PIN mới và đồng bộ retry tại [src/CustomerWindow.java](src/CustomerWindow.java#L572-L745).
- **Mua hàng (có phê duyệt nhân viên)**: Chọn hàng, tạo giỏ, gửi yêu cầu phê duyệt; nếu được chấp nhận thì verify PIN, trừ tiền, ghi thẻ, log DB, broadcast số dư mới tại [src/CustomerWindow.java](src/CustomerWindow.java#L747-L1022).
- **Nạp tiền (có phê duyệt nhân viên)**: Chọn mệnh giá và phương thức (Tiền mặt/QR), gửi yêu cầu phê duyệt; nếu chấp nhận thì verify PIN, cộng tiền, ghi thẻ, log DB, broadcast tại [src/CustomerWindow.java](src/CustomerWindow.java#L1024-L1210).

## Giao Thức & Bảo Mật (tóm tắt ứng dụng)
- **APDU**: Dựng/gửi lệnh qua helpers ở [src/CardHelper.java](src/CardHelper.java#L18-L40).
  - `READ (0xB0)`: Đọc 80 bytes; PII và số dư/hạn tập được mã hóa, xem [src/CardHelper.java](src/CardHelper.java#L49-L89;L122-L152).
  - `WRITE (0xD0)`: Ghi 80 bytes đã mã hóa; yêu cầu trạng thái xác thực PIN đối với dữ liệu đã có, xem [src/CardHelper.java](src/CardHelper.java#L63-L89) và các luồng ghi ở Customer/Admin.
  - `VERIFY_PIN (0x20)`: Xác thực PIN, trả dữ liệu đã giải mã khi đúng, xem [src/CardHelper.java](src/CardHelper.java#L91-L111;L154-L210).
  - `CHANGE_PIN (0x24)`: Đổi PIN người dùng (cần verify), xem [src/CustomerWindow.java](src/CustomerWindow.java#L572-L745).
  - `ADMIN_UNLOCK (0xAA)` / `ADMIN_RESET_PIN (0xAB)`: Đặc quyền Admin, xem [src/FunctionPanel.java](src/FunctionPanel.java#L706-L786;L792-L923).
  - `GET_PUBLIC_KEY (0x82)` / `SIGN_CHALLENGE (0x88)`: Đăng ký và xác thực RSA, xem [src/RsaKeyService.java](src/RsaKeyService.java#L41;L83-L116).
  - `AVATAR_CLEAR (0xC3)` / `AVATAR_WRITE (0xC0)`: Quản lý ảnh đại diện, xem [src/FunctionPanel.java](src/FunctionPanel.java#L461-L479) và builder [src/CardHelper.java](src/CardHelper.java#L113-L121;L123-L139).
- **Bảo mật dữ liệu**:
  - Số dư, hạn tập, DOB, họ tên… nằm trong khối mã hóa AES-128; chỉ hiển thị đầy đủ sau `VERIFY_PIN`.
  - PIN hash: SHA-256 truncated 16 bytes.
  - RSA-1024: xác thực thẻ thật bằng challenge-response.

## Tương Tác Database
- Thành viên: CRUD và liệt kê ở [src/MembersDao.java](src/MembersDao.java#L60-L86;L113-L132;L146-L157).
- Giao dịch: ghi log nạp/mua/gia hạn ở [src/TransactionsDao.java](src/TransactionsDao.java#L38-L64).
- Bảng hiển thị: [src/MembersTableDialog.java](src/MembersTableDialog.java#L1-L120), [src/TransactionsTableDialog.java](src/TransactionsTableDialog.java#L19).

## Luồng liên cửa sổ (Customer ↔ Staff/Admin)
- Broadcast thẻ sau verify PIN: [src/CardEventBroadcaster.java](src/CardEventBroadcaster.java#L34-L42) gọi listener ở FunctionPanel để hiển thị dữ liệu giải mã.
- Yêu cầu phê duyệt: Khách gửi `requestPurchaseApproval`/`requestTopupApproval` tại [src/CardEventBroadcaster.java](src/CardEventBroadcaster.java#L44-L56;L58-L66), Staff phê duyệt qua dialog tại [src/FunctionPanel.java](src/FunctionPanel.java#L624-L688).

---
Tài liệu này bám sát hành vi thực tế trong mã nguồn hiện tại để phục vụ viết báo cáo chức năng. Nếu cần thêm chi tiết (UI hình ảnh, mock luồng), vui lòng cho biết để bổ sung.
