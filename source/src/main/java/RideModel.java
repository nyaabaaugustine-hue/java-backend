
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.pengrad.telegrambot.model.Location;

/**
 * RideModel.java
 * 
 * Responsible for providing ride information
 * 
 * @author gusanthiago
 * @author hmmoreira
 */
public class RideModel implements Subject {

	private static RideModel instance;
	private static TransportService transportService;
	private List<Observer> observers = new LinkedList<Observer>();

	public static RideModel getInstance() {
		if (instance == null) {
			instance = new RideModel();
		}
		// Initialize Mock Service instead of Uber SDK
		transportService = new MockTransportService();
		return instance;
	}
	
	public void registerObserver(Observer observer) {
		observers.add(observer);
	}
	
	public void removeObserver(Observer observer) {
		observers.remove(observer);
	}
	
	public void notifyObservers(long chatId, String data){
		for(Observer observer:observers){
			observer.update(chatId, data);
		}
	}
	
	/**
	 * Ride
	 * 
	 * @param productFare
	 * @return
	 */
	public TransportRide requestRide(ProductFare productFare) {
		
		TransportRide ride = null;
		try {
			ride = transportService.requestRide(productFare.getRideEstimate());
			System.out.println("Ride request " + ride.getStatus());
			return ride;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ride;
		
	}

	public TransportRide selectRide(TransportRide ride) {
		
		try {
			return transportService.getRideStatus(ride.getRideId());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * Return list from ProductFare
	 * 
	 * @param locationStart
	 * @param locationFinish
	 * @return List<ProductFare>
	 */
	public List<ProductFare> selectAllProductsFares(GeoLocation locationStart, GeoLocation locationFinish) {
		
		List<ProductFare> productsFares = new ArrayList<ProductFare>();
		try {
			
			List<TransportProduct> products = transportService.getProducts(locationStart);
			
			for (TransportProduct product : products) {
				ProductFare productFare = this.buildProductFare(product, locationStart, locationFinish, null, null);
				productsFares.add(productFare);
				System.out.println(
						productFare.getProduct().getDisplayName() +
						" - " + productFare.getRideEstimate().getFare().getDisplay());
			
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return productsFares;
	}

	/**
	 * Build ProductFare
	 * @param product
	 * @param start
     * @param end
	 * @param locationStart
	 * @param locationFinish
	 * @return ProductFare
	 */
	private ProductFare buildProductFare(TransportProduct product, GeoLocation start, GeoLocation end, Location locationStart, Location locationFinish) {
		ProductFare productFare = new ProductFare();
		try {
			TransportEstimate rideEstimate = transportService.estimateRide(product, start, end);
			productFare.setProduct(product);
			productFare.setRideEstimate(rideEstimate);
			// Only set locationStart and locationFinish if they are not null
			if (locationStart != null) {
				productFare.setLocationStart(locationStart);
			}
			if (locationFinish != null) {
				productFare.setLocationFinish(locationFinish);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return productFare;
	}

}
