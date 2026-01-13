
public class TransportEstimate {
    private TransportProduct product;
    private TransportFare fare;
    private int pickupDuration;
    private int tripDuration;

    public TransportEstimate(TransportProduct product, TransportFare fare, int pickupDuration, int tripDuration) {
        this.product = product;
        this.fare = fare;
        this.pickupDuration = pickupDuration;
        this.tripDuration = tripDuration;
    }

    public TransportProduct getProduct() { return product; }
    public TransportFare getFare() { return fare; }
    public int getPickupDuration() { return pickupDuration; }
    public int getTripDuration() { return tripDuration; }
}
