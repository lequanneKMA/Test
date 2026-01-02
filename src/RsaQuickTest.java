public class RsaQuickTest {
    public static void main(String[] args) {
        try {
            PcscClient pcsc = new PcscClient();
            pcsc.connectFirstPresentOrFirst();

            // Select applet (same AID used in CustomerWindow)
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(
                0x00, 0xA4, 0x04, 0x00,
                new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00}
            );
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                System.out.println("[FAIL] Select applet failed: SW=0x" + Integer.toHexString(selectResp.getSW()).toUpperCase());
                return;
            }

            // Read card to get userId
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(CardHelper.buildReadCommand());
            if ((readResp.getSW() & 0xFF00) == 0x6C00) {
                int correctLE = readResp.getSW2();
                readResp = pcsc.transmit(new javax.smartcardio.CommandAPDU(0x00, CardHelper.INS_READ, 0x00, 0x00, correctLE));
            }
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                System.out.println("[FAIL] Read card failed: SW=0x" + Integer.toHexString(readResp.getSW()).toUpperCase());
                return;
            }
            CardData card = CardHelper.parseReadResponse(readResp.getData());
            int userId = card.userId;
            System.out.println("[INFO] UserID on card: " + userId);

            // Register public key to DB
            RsaKeyService.registerCardPublicKey(pcsc, userId);
            System.out.println("[OK] Stored RSA public key (modulus/exponent) for user " + userId + " into DB.");

            // Verify challenge-response
            boolean ok = RsaKeyService.verifyCardLogin(pcsc, userId);
            // ... (Đoạn code trên của bạn giữ nguyên) ...
            System.out.println("[RESULT] RSA verify: " + (ok ? "SUCCESS" : "FAIL"));

            // ... (Đoạn trên giữ nguyên) ...

// =================================================================
// BẮT ĐẦU ĐOẠN TEST BỔ SUNG: GIẢ LẬP HACKER (TAMPER TEST)
// =================================================================
System.out.println("\n--- START TAMPER TEST (GIẢ LẬP HACKER) ---");

MembersDao dao = new MembersDao();
MemberRecord originalRec = dao.getByUserId(userId); 

// 1. Ghi đè Key dỏm vào DB
String fakeModulus = originalRec.rsaModulusHex.substring(0, originalRec.rsaModulusHex.length() - 2) + "00";

// --- SỬA DÒNG NÀY (Đổi tên hàm) ---
dao.updateRsaPublicKeyHex(userId, fakeModulus, originalRec.rsaExponentHex); 
// ----------------------------------

System.out.println("[TEST] Da sua doi public key trong DB (gia lap db bi hack hoac the gia).");

// 2. Thử Verify lại lần nữa
boolean isHackSuccess = RsaKeyService.verifyCardLogin(pcsc, userId);

// 3. Kết quả mong đợi là FALSE
if (!isHackSuccess) {
    System.out.println("[PASS] He thong phat hien the sai/key sai chinh xac! (Login Failed as expected)");
} else {
    System.out.println("[DANGER] CANH BAO: Verify van thanh cong du sai Key! Code co loi logic!");
}

// 4. Tra lai Key xin cho DB (DDon dep hien truong)
dao.updateRsaPublicKeyHex(userId, originalRec.rsaModulusHex, originalRec.rsaExponentHex);
// --------------------------------------

System.out.println("--- END TAMPER TEST (Đã khôi phục DB) ---\n");
            // Optional: print hex key from DB
            try {
                MemberRecord rec = dao.getByUserId(userId);
                System.out.println("modulus(hex)  = " + rec.rsaModulusHex);
                System.out.println("exponent(hex) = " + rec.rsaExponentHex);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
