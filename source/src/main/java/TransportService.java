
import java.util.List;

public interface TransportService {
    List<TransportProduct> getProducts(GeoLocation location);
    TransportEstimate estimateRide(TransportProduct product, GeoLocation start, GeoLocation end);
    TransportRide requestRide(TransportEstimate estimate);
    TransportRide getRideStatus(String rideId);
}
