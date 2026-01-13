
import com.pengrad.telegrambot.model.Location;

public class ProductFare {
	
	TransportProduct product;
	TransportEstimate rideEstimate;
	TransportRide ride;
	Location locationStart;
	Location locationFinish;
	
	public Location getLocationStart() {
		return locationStart;
	}
	public void setLocationStart(Location locationStart) {
		this.locationStart = locationStart;
	}
	public Location getLocationFinish() {
		return locationFinish;
	}
	public void setLocationFinish(Location locationFinish) {
		this.locationFinish = locationFinish;
	}
	public TransportRide getRide() {
		return ride;
	}
	public void setRide(TransportRide ride) {
		this.ride = ride;
	}
	public TransportProduct getProduct() {
		return product;
	}
	public void setProduct(TransportProduct product) {
		this.product = product;
	}
	public TransportEstimate getRideEstimate() {
		return rideEstimate;
	}
	public void setRideEstimate(TransportEstimate rideEstimate) {
		this.rideEstimate = rideEstimate;
	}
}
