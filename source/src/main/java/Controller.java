import java.util.List;

import com.pengrad.telegrambot.model.Location;
import com.pengrad.telegrambot.model.Update;


/**
 * Controller.java
 * 
 * Provides interface for Controllers
 * 
 * @author gusanthiago
 * @author hmmoreira
 */
public interface Controller {

	public TransportRide request(Update update, ProductFare product);

	public TransportRide statusForRide(Update update, TransportRide ride);

	public List<ProductFare> findAllProducts(Location locationStart, Location locationFinish);
}
