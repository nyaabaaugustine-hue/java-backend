import java.util.List;

import com.pengrad.telegrambot.model.Location;
import com.pengrad.telegrambot.model.Update;

/**
 * RideController.java
 * 
 * Responsible for controlling ride actions
 * 
 * @author gusanthiago
 * @author hmmoreira
 *
 */
public class RideController implements Controller {

	private RideModel rideModel;
	private View view;
	
	public RideController(RideModel model, View view){
		this.rideModel = model; //connection Controller -> Model
		this.view = view; //connection Controller -> View
	}
	
	public TransportRide request(Update update, ProductFare productFare) {
		view.sendTypingMessage(update);
		return rideModel.requestRide(productFare);
	}
	
	public TransportRide statusForRide(Update update, TransportRide ride) {
		view.sendTypingMessage(update);
		return rideModel.selectRide(ride);	
	}
	
	public List<ProductFare> findAllProducts(Location locationStart, Location locationFinish) {
		return this.rideModel.selectAllProductsFares(locationStart, locationFinish);
	}
	
}
