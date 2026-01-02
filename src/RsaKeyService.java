import javax.smartcardio.ResponseAPDU;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

/**
 * RSA registration and login verification helpers for SmartCard.
 */
public class RsaKeyService {
    /**
     * Convert byte[] to HEX string (uppercase, no spaces)
     */
    public static String bytesToHex(byte[] data) {
        if (data == null) return null;
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    /**
     * Convert HEX string to byte[] (ignores spaces)
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
     * Registration: Read RSA public key from card and store into DB as hex strings.
     */
    public static void registerCardPublicKey(PcscClient pcsc, int userId) throws Exception {
        ResponseAPDU resp = pcsc.transmit(CardHelper.buildGetPublicKeyCommand());
        if (!CardHelper.isSuccess(resp)) {
            throw new IllegalStateException("GET_PUBLIC_KEY failed: SW=0x" + Integer.toHexString(resp.getSW()).toUpperCase());
        }
        byte[] data = resp.getData();
        if (data == null || data.length < 131) {
            // Accept shorter if card returns minimal length; modulus then exponent
            // Try to split assuming 1024-bit modulus
            if (data == null || data.length < 131 - 3) {
                throw new IllegalStateException("Unexpected public key length: " + (data == null ? 0 : data.length));
            }
        }
        // Split into modulus and exponent; prefer 128+3 layout
        byte[] modulus;
        byte[] exponent;
        if (data.length >= 131) {
            modulus = new byte[128];
            System.arraycopy(data, 0, modulus, 0, 128);
            exponent = new byte[data.length - 128];
            System.arraycopy(data, 128, exponent, 0, exponent.length);
        } else {
            // Fallback: assume last 3 bytes are exponent
            int expLen = 3;
            exponent = new byte[expLen];
            System.arraycopy(data, data.length - expLen, exponent, 0, expLen);
            modulus = new byte[data.length - expLen];
            System.arraycopy(data, 0, modulus, 0, modulus.length);
        }

        String modHex = bytesToHex(modulus);
        String expHex = bytesToHex(exponent);

        MembersDao dao = new MembersDao();
        dao.updateRsaPublicKeyHex(userId, modHex, expHex);
    }

    /**
     * Verify login via RSA challenge-response using public key from DB.
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

        // Construct PublicKey; handle potential leading 0x00 sign byte by using positive BigInteger
        BigInteger n = new BigInteger(1, modulusBytes);
        BigInteger e = new BigInteger(1, exponentBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pub = kf.generatePublic(spec);

        // Create random 32-byte challenge
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);

        // Send to card to sign
        ResponseAPDU sigResp = pcsc.transmit(CardHelper.buildSignChallengeCommand(challenge));
        if (!CardHelper.isSuccess(sigResp)) {
            throw new IllegalStateException("SIGN_CHALLENGE failed: SW=0x" + Integer.toHexString(sigResp.getSW()).toUpperCase());
        }
        byte[] signature = sigResp.getData();

        // Verify signature with SHA1withRSA (must match applet)
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(pub);
        verifier.update(challenge);
        return verifier.verify(signature);
    }
}
