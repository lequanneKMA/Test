import java.util.ArrayList;
import java.util.List;

/**
 * Singleton broadcaster để sync thông tin thẻ giữa Customer và Staff windows
 */
public class CardEventBroadcaster {
    private static CardEventBroadcaster instance;
    private List<CardEventListener> listeners = new ArrayList<>();
    private List<PurchaseRequestListener> purchaseListeners = new ArrayList<>();
    private List<TopupRequestListener> topupListeners = new ArrayList<>();
    
    public interface CardEventListener {
        void onCardSwiped(CardData card);
    }
    
    public interface PurchaseRequestListener {
        boolean onPurchaseRequest(List<CartItem> items, int totalPrice);
    }
    
    public interface TopupRequestListener {
        boolean onTopupRequest(int amount, String paymentMethod);
    }
    
    public static class CartItem {
        public StoreItem item;
        public int quantity;
        
        public CartItem(StoreItem item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }
    }
    
    private CardEventBroadcaster() {}
    
    public static CardEventBroadcaster getInstance() {
        if (instance == null) {
            instance = new CardEventBroadcaster();
        }
        return instance;
    }
    
    public void addCardListener(CardEventListener listener) {
        listeners.add(listener);
    }
    
    public void addPurchaseListener(PurchaseRequestListener listener) {
        purchaseListeners.add(listener);
    }
    
    public void addTopupListener(TopupRequestListener listener) {
        topupListeners.add(listener);
    }
    
    public void broadcastCardSwipe(CardData card) {
        for (CardEventListener listener : listeners) {
            listener.onCardSwiped(card);
        }
    }
    
    public boolean requestPurchaseApproval(List<CartItem> items, int totalPrice) {
        for (PurchaseRequestListener listener : purchaseListeners) {
            return listener.onPurchaseRequest(items, totalPrice);
        }
        return false; // Không có nhân viên online
    }
    
    public boolean requestTopupApproval(int amount, String paymentMethod) {
        for (TopupRequestListener listener : topupListeners) {
            return listener.onTopupRequest(amount, paymentMethod);
        }
        return false; // Không có nhân viên online
    }
}
