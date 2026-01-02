/**
 * StoreItem - Sản phẩm trong cửa hàng
 */
public class StoreItem {
    public int id;
    public String name;
    public int price;
    public int quantity;

    public StoreItem(int id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = 0;
    }

    @Override
    public String toString() {
        return name + " - " + price + "₫";
    }
}
