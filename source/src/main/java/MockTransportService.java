
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockTransportService implements TransportService {

    private Map<String, TransportRide> rides = new HashMap<>();

    @Override
    public List<TransportProduct> getProducts(GeoLocation location) {
        List<TransportProduct> products = new ArrayList<>();
        products.add(new TransportProduct("taxi", "Taxi 🚕", "Standard taxi ride", 4));
        products.add(new TransportProduct("moto", "Moto 🏍️", "Fast motorbike ride", 1));
        products.add(new TransportProduct("tuk", "Tuk Tuk 🛺", "Tricycle ride", 3));
        products.add(new TransportProduct("delivery", "Delivery 🚚", "Package delivery", 0));
        return products;
    }

    @Override
    public TransportEstimate estimateRide(TransportProduct product, GeoLocation start, GeoLocation end) {
        double km = haversineKm(start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
        double basePrice;
        double perKm;
        if ("taxi".equals(product.getProductId())) {
            basePrice = 5.0; perKm = 2.0;
        } else if ("moto".equals(product.getProductId())) {
            basePrice = 3.0; perKm = 1.2;
        } else if ("tuk".equals(product.getProductId())) {
            basePrice = 4.0; perKm = 1.5;
        } else {
            basePrice = 5.0; perKm = 2.5;
        }
        double price = basePrice + (km * perKm);
        String priceStr = String.format("GHS %.2f", price);
        TransportFare fare = new TransportFare(UUID.randomUUID().toString(), String.valueOf(price), "GHS", priceStr);
        return new TransportEstimate(product, fare, 5, (int)Math.max(3, km * 3));
    }

    @Override
    public TransportRide requestRide(TransportEstimate estimate) {
        String rideId = UUID.randomUUID().toString();
        TransportRide ride = new TransportRide(rideId, "processing", estimate.getProduct());
        ride.setDriverName("Mock Driver");
        ride.setVehicleModel("Cyber Vehicle");
        ride.setVehiclePlate("CYBER-2077");
        rides.put(rideId, ride);
        return ride;
    }

    @Override
    public TransportRide getRideStatus(String rideId) {
        TransportRide ride = rides.get(rideId);
        if (ride != null) {
            if ("processing".equals(ride.getStatus())) {
                ride.setStatus("accepted");
            } else if ("accepted".equals(ride.getStatus())) {
                ride.setStatus("arriving");
            } else if ("arriving".equals(ride.getStatus())) {
                ride.setStatus("in_progress");
            } else if ("in_progress".equals(ride.getStatus())) {
                ride.setStatus("completed");
            }
        }
        return ride;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
}
