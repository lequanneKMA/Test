import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogger {
    private static final String LOG_FILE = "failed_transactions.log";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void saveEmergencyLog(String type, int userId, int amount, int newBalance, String extra) {
        String time = LocalDateTime.now().format(TS);
        String line = String.format("Time=%s | Type=%s | UserID=%d | Amount=%d | NewBalance=%d | Extra=%s%n",
                time, type, userId, amount, newBalance, extra != null ? extra : "");
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(line);
            fw.flush();
        } catch (IOException ioe) {
            // As last resort, print to stderr to not lose context
            System.err.println("[EMERGENCY-LOG-FAIL] " + ioe.getMessage());
            System.err.println(line);
        }
    }

    public static void logTopup(int userId, int amount, int newBalance) {
        saveEmergencyLog("TOPUP", userId, amount, newBalance, null);
    }

    public static void logPurchase(int userId, int amount, int newBalance, String itemsJson) {
        saveEmergencyLog("PURCHASE", userId, amount, newBalance, itemsJson);
    }

    public static void logRenew(int userId, int amount, int newBalance, int daysAdded) {
        saveEmergencyLog("RENEW", userId, amount, newBalance, "daysAdded=" + daysAdded);
    }
}
