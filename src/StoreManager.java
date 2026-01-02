import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StoreManager - Quản lý danh sách hàng hóa
 */
public class StoreManager {
    private final List<StoreItem> items = new ArrayList<>();

    public StoreManager() {
        // Khởi tạo danh sách hàng
        items.add(new StoreItem(1, "Nước Uống 500ml", 15000));
        items.add(new StoreItem(2, "Towel Gym", 50000));
        items.add(new StoreItem(3, "Dây Đai Cơ Bụng", 120000));
        items.add(new StoreItem(4, "Bao Tay Tập", 35000));
        items.add(new StoreItem(5, "Áo Thun Gym", 100000));
        items.add(new StoreItem(6, "Bít Tất", 25000));
        items.add(new StoreItem(7, "Túi Quây Hông", 80000));
        items.add(new StoreItem(8, "Dây Uốn Eo", 75000));
    }

    public List<StoreItem> getItems() {
        return items;
    }

    public StoreItem getItem(int id) {
        return items.stream()
                .filter(item -> item.id == id)
                .findFirst()
                .orElse(null);
    }

    public int getTotalPrice(List<StoreItem> cart) {
        return cart.stream()
                .mapToInt(item -> item.price * item.quantity)
                .sum();
    }
}
