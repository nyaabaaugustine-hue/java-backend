
public class TransportProduct {
    private String productId;
    private String displayName;
    private String description;
    private int capacity;
    private String image;

    public TransportProduct(String productId, String displayName, String description, int capacity) {
        this.productId = productId;
        this.displayName = displayName;
        this.description = description;
        this.capacity = capacity;
    }

    public String getProductId() { return productId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getCapacity() { return capacity; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
