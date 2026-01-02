import javax.smartcardio.*;
import java.util.List;

public final class PcscClient implements AutoCloseable {
    private final TerminalFactory terminalFactory;
    private CardTerminal terminal;
    private Card card;
    private CardChannel channel;

    public PcscClient() {
        this.terminalFactory = TerminalFactory.getDefault();
    }

    public List<CardTerminal> listTerminals() throws CardException {
        return terminalFactory.terminals().list();
    }

    public PcscClient connectFirstPresentOrFirst() throws Exception {
        List<CardTerminal> terminals = listTerminals();
        if (terminals.isEmpty()) {
            throw new IllegalStateException("No PC/SC terminals found. On Windows, verify Smart Card service is running and a reader (or virtual reader) is installed.");
        }

        for (CardTerminal t : terminals) {
            try {
                if (t.isCardPresent()) {
                    return connect(t);
                }
            } catch (CardException ignored) {
                // Some terminals/drivers may throw; skip.
            }
        }

        return connect(terminals.get(0));
    }

    public PcscClient connect(CardTerminal terminal) throws CardException {
        this.terminal = terminal;
        this.card = terminal.connect("*");
        this.channel = card.getBasicChannel();
        return this;
    }

    public PcscClient waitForCardPresent(long timeoutMs) throws CardException {
        if (terminal == null) {
            throw new IllegalStateException("Terminal not selected yet");
        }
        terminal.waitForCardPresent(timeoutMs);
        return this;
    }

    public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
        if (channel == null) {
            throw new IllegalStateException("Not connected");
        }
        return channel.transmit(apdu);
    }

    public static String toHex(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        try {
            if (card != null) {
                card.disconnect(false);
            }
        } catch (CardException ignored) {
        } finally {
            card = null;
            channel = null;
            terminal = null;
        }
    }
}
