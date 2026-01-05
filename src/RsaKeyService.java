import javax.smartcardio.ResponseAPDU;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

/**
 * RSA Key Service: Đăng ký và xác thực khóa công khai RSA với thẻ thông minh.
 */
public class RsaKeyService {
    /**
     * Convert byte[] to HEX string
     */
    public static String bytesToHex(byte[] data) {
        if (data == null) return null;
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    /**
     * convert HEX string to byte[]
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        String s = hex.replaceAll("\\s+", "");
        if ((s.length() % 2) != 0) throw new IllegalArgumentException("Hex length must be even");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /**
     * Đăng ký khóa công khai RSA từ thẻ vào Database.
     */
    public static void registerCardPublicKey(PcscClient pcsc, int userId) throws Exception {
        ResponseAPDU resp = pcsc.transmit(CardHelper.buildGetPublicKeyCommand());
        if (!CardHelper.isSuccess(resp)) {
            throw new IllegalStateException("GET_PUBLIC_KEY failed: SW=0x" + Integer.toHexString(resp.getSW()).toUpperCase());
        }
        byte[] data = resp.getData();
        if (data == null || data.length < 131) {
            throw new IllegalStateException("Unexpected public key length: " + (data == null ? 0 : data.length));
        }
        // tách modulus và exponent
        int expLen = 3;
        if (data.length < expLen + 1) {
            throw new IllegalStateException("Public key too short: " + data.length);
        }
        byte[] exponent = new byte[expLen];
        System.arraycopy(data, data.length - expLen, exponent, 0, expLen);
        byte[] modulus = new byte[data.length - expLen];
        System.arraycopy(data, 0, modulus, 0, modulus.length);

        String modHex = bytesToHex(modulus);
        String expHex = bytesToHex(exponent);

        MembersDao dao = new MembersDao();
        dao.updateRsaPublicKeyHex(userId, modHex, expHex);
    }

    /**
     * Kiểm tra đăng nhập thẻ bằng xác thực RSA.
     */
    public static boolean verifyCardLogin(PcscClient pcsc, int userId) throws Exception {
        MembersDao dao = new MembersDao();
        MemberRecord rec = dao.getByUserId(userId);
        if (rec == null) throw new IllegalArgumentException("User not found: " + userId);
        if (rec.rsaModulusHex == null || rec.rsaExponentHex == null) {
            throw new IllegalStateException("RSA public key not registered for user " + userId);
        }

        byte[] modulusBytes = hexToBytes(rec.rsaModulusHex);
        byte[] exponentBytes = hexToBytes(rec.rsaExponentHex);

        // Xây dựng PublicKey từ modulus và exponent
        BigInteger n = new BigInteger(1, modulusBytes);
        BigInteger e = new BigInteger(1, exponentBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pub = kf.generatePublic(spec);

        // Tạo challenge ngẫu nhiên 32 bytes
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);

        // Gửi challenge đến thẻ để ký
        ResponseAPDU sigResp = pcsc.transmit(CardHelper.buildSignChallengeCommand(challenge));
        if (!CardHelper.isSuccess(sigResp)) {
            throw new IllegalStateException("SIGN_CHALLENGE failed: SW=0x" + Integer.toHexString(sigResp.getSW()).toUpperCase());
        }
        byte[] signature = sigResp.getData();

        // Verify signature với SHA1withRSA 
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(pub);
        verifier.update(challenge);
        return verifier.verify(signature);
    }
}
