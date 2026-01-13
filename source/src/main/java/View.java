
import java.util.List;

import com.pengrad.telegrambot.TelegramBot;
// import removed in newer versions
import com.pengrad.telegrambot.model.Location;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendLocation;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import okhttp3.OkHttpClient;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.Credentials;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;


/**
 * View.java
 * 
 * Created to monitor user interaction
 *
 * @author gusanthiago
 * @author hmmoreira
 */
public class View implements Observer {

	TelegramBot bot;	
	Long dispatchChatId = null;
	java.io.File dispatchFile = new java.io.File("dispatch_chat_id.txt");
	
	/**
	 * Request and response for Telegram API 
	 */
	GetUpdatesResponse updatesResponse;
	SendResponse sendResponse;
	BaseResponse baseResponse;
		
	/**
	 * Models
	 */
	private RideModel rideModel;
	
	/**
	 * Interface Controller
	 */
	private Controller controller;
	
	/**
	 * Queue for Message
	 */
	int queuesIndex=0;
	
	/**
	 * Flags for states
	 */
	boolean isRequestRide = false;
	boolean hasStartLocation = false;
	boolean onTheRun = false;
	
	TransportRide myRide = null;

	Location locationStart = null;
	Location locationFinish = null;
	
	List<ProductFare> listProductFare;
	
	/**
	 * Construtor da View
	 * @param model
	 */
	public View(RideModel model){
		this.rideModel = model; 
		String token = null;
		String tokenSource = null;
		try {
			java.io.File f1 = new java.io.File(".env");
			java.io.File f2 = new java.io.File("driber/source/.env");
			java.io.File f3 = new java.io.File("source/.env");
			java.io.File target = null;
			if (f1.exists()) target = f1;
			else if (f2.exists()) target = f2;
			else if (f3.exists()) target = f3;
			if (target != null) {
				java.util.List<String> lines = java.nio.file.Files.readAllLines(target.toPath(), java.nio.charset.StandardCharsets.UTF_8);
				for (String line : lines) {
					String l = line.trim();
					if (l.startsWith("TELEGRAM_BOT_TOKEN=")) {
						String[] parts = l.split("=", 2);
						if (parts.length == 2) token = parts[1].trim();
					}
				}
				if (token != null && token.length() > 0) {
					tokenSource = "file:" + target.getPath();
				}
			}
		} catch (Exception e) {
		}
		if (token == null || token.length() == 0) {
			token = System.getenv("TELEGRAM_BOT_TOKEN");
			if (token != null && token.length() > 0) {
				tokenSource = "env";
			}
		}
		if (token != null && token.length() > 0) {
			OkHttpClient.Builder b = new OkHttpClient.Builder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS);
			try {
				String proxyUrl = System.getenv("HTTPS_PROXY");
				if (proxyUrl == null || proxyUrl.length() == 0) proxyUrl = System.getenv("HTTP_PROXY");
				if (proxyUrl != null && proxyUrl.length() > 0) {
					URI u = new URI(proxyUrl);
					String host = u.getHost();
					int port = u.getPort() > 0 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
					if (host != null) {
						Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
						b.proxy(proxy);
						if (u.getUserInfo() != null && u.getUserInfo().contains(":")) {
							final String[] up = u.getUserInfo().split(":", 2);
							final String cred = Credentials.basic(up[0], up[1]);
							b.proxyAuthenticator(new Authenticator() {
								public Request authenticate(Route route, Response response) {
									return response.request().newBuilder().header("Proxy-Authorization", cred).build();
								}
							});
						}
					}
				}
			} catch (Exception e) {
			}
			System.out.println("Using TELEGRAM_BOT_TOKEN from " + (tokenSource != null ? tokenSource : "unknown"));
			this.bot = new TelegramBot.Builder(token).okHttpClient(b.build()).build();
		} else {
			this.bot = null;
			System.out.println("Missing TELEGRAM_BOT_TOKEN. Bot will not start.");
		}
		if (this.bot != null) {
			try { this.bot.execute(new DeleteWebhook()); } catch (Exception e) {}
			try {
				com.pengrad.telegrambot.request.GetMe getMe = new com.pengrad.telegrambot.request.GetMe();
				com.pengrad.telegrambot.response.GetMeResponse meResp = this.bot.execute(getMe);
				if (meResp == null || !meResp.isOk()) {
					System.out.println("Cannot reach Telegram API. Check network/proxy settings.");
				} else {
					com.pengrad.telegrambot.model.User me = meResp.user();
					System.out.println("Bot online: id=" + (me != null ? me.id() : "unknown") + " username=" + (me != null ? me.username() : "unknown"));
				}
			} catch (Exception e) {
				System.out.println("Startup health check failed: " + e.getMessage());
			}
			try {
				GetUpdatesResponse initResp = this.bot.execute(new GetUpdates().limit(1).timeout(0));
				List<Update> init = initResp != null ? initResp.updates() : null;
				if (init != null && !init.isEmpty()) {
					int lastId = init.get(init.size()-1).updateId();
					this.queuesIndex = lastId + 1;
					System.out.println("Initialized update offset to " + this.queuesIndex);
				}
			} catch (Exception e) {}
			if (this.dispatchChatId != null) {
				try { bot.execute(new SendMessage(this.dispatchChatId, "Bot online and ready.")); } catch (Exception e) {}
			}
		}
		try {
			String groupIdStr = System.getenv("DISPATCH_CHAT_ID");
			if (groupIdStr == null || groupIdStr.length() == 0) {
				java.io.File f1 = new java.io.File(".env");
				if (f1.exists()) {
					java.util.List<String> lines = java.nio.file.Files.readAllLines(f1.toPath(), java.nio.charset.StandardCharsets.UTF_8);
					for (String line : lines) {
						String l = line.trim();
						if (l.startsWith("DISPATCH_CHAT_ID=")) {
							String[] parts = l.split("=", 2);
							if (parts.length == 2) groupIdStr = parts[1].trim();
						}
					}
				}
			}
			if (groupIdStr != null && groupIdStr.length() > 0) {
				this.dispatchChatId = Long.parseLong(groupIdStr);
			}
		} catch (Exception e) {
			System.out.println("Invalid DISPATCH_CHAT_ID: "+e.getMessage());
		}
		if (this.dispatchChatId == null) {
			try {
				if (dispatchFile.exists()) {
					String s = new String(java.nio.file.Files.readAllBytes(dispatchFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
					if (s.length() > 0) this.dispatchChatId = Long.parseLong(s);
				}
			} catch (Exception e) {}
		}
	}

	public void setController(Controller controller){ //Strategy Pattern
		this.controller = controller;
	}
	
	public void receiveUsersMessages() {

		if (bot == null) {
			System.out.println("Bot not started. Check TELEGRAM_BOT_TOKEN (env or .env).");
			return;
		}
		long backoffMs = 5000;
		while (true){
			try {
			updatesResponse =  bot.execute(new GetUpdates().limit(100).timeout(30).offset(queuesIndex));
			
			//Queue of messages
			List<Update> updates = updatesResponse != null ? updatesResponse.updates() : null;
			if (updates == null) {
				System.out.println("Poll tick: no updates");
				try { Thread.sleep(1000); } catch (InterruptedException e) {}
				continue;
			}
			System.out.println("Updates received: " + updates.size());
			backoffMs = 5000;
			// advance offset to the last update id to avoid re-reading same updates
			try {
				if (!updates.isEmpty()) {
					int lastId = updates.get(updates.size()-1).updateId();
					if (lastId + 1 > queuesIndex) {
						queuesIndex = lastId + 1;
					}
				}
			} catch (Exception e) {}
			
			for (Update update : updates) {
				if (update.message() != null && update.message().text() != null) {
					System.out.println("Text update: chat " + update.message().chat().id() + " text=" + update.message().text());
				} else {
					System.out.println("Non-text update received: id=" + update.updateId());
				}
				// always advance offset to avoid re-fetching the same non-message updates
				int nextOffset = update.updateId() + 1;
				if (nextOffset > queuesIndex) {
					queuesIndex = nextOffset;
				}
				if (update.message() == null) {
					continue;
				}
				if (update.message().text() != null) {
					ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "text", update.message().text());
				} else if (update.message().location() != null) {
					ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "location", update.message().location().latitude()+" , "+update.message().location().longitude());
				}
				//updating queue's index
				queuesIndex = update.updateId()+1;	
				this.buildInteraction(update);
				
			}

			} catch (Exception ex) {
				System.out.println("Polling error: " + ex.getMessage());
				try { Thread.sleep(backoffMs); } catch (InterruptedException ie) {}
				backoffMs = Math.min(backoffMs * 2, 60000);
				continue;
			}
		}
		
	}
	
	public void buildInteraction(Update update) {
		
		String messageText = null;
		
		if (update.message() == null) {
			return;
		}
		
		if (update.message().text() != null && update.message().text().toUpperCase().equals("/START")) {
			this.cleanStates();
			Keyboard keyboard = new ReplyKeyboardMarkup(
			        new KeyboardButton[] {
			                new KeyboardButton("Yes, let's go"),
			                new KeyboardButton("No")
		                }
			).oneTimeKeyboard(true);                
			messageText = "Hello " + update.message().from().firstName() + ", ready to ride?";
			System.out.println("Processing /start for chat " + update.message().chat().id());
			this.sendMessageWithKeyBoard(
				update.message().chat().id(), 
				messageText, 
				keyboard
			);
			return;
		}
		if (update.message().text() != null && update.message().text().toUpperCase().startsWith("/DIAG")) {
			StringBuilder sb = new StringBuilder();
			sb.append("Diagnostics\n");
			sb.append("Bot: ");
			try {
				BaseResponse ok = this.bot.execute(new com.pengrad.telegrambot.request.GetMe());
				sb.append(ok != null && ok.isOk() ? "OK" : "FAIL");
			} catch (Exception e) {
				sb.append("ERROR: ").append(e.getMessage());
			}
			sb.append("\nDispatch ID: ").append(this.dispatchChatId != null ? String.valueOf(this.dispatchChatId) : "NOT SET");
			this.sendMessage(update.message().chat().id(), sb.toString());
			if (this.dispatchChatId != null) {
				try {
					this.notifyGroup("Diagnostic ping to drivers group");
					this.sendMessage(update.message().chat().id(), "Dispatch ping sent.");
				} catch (Exception e) {
					this.sendMessage(update.message().chat().id(), "Dispatch ping failed: "+e.getMessage());
				}
			}
			return;
		}
		if (update.message().text() != null && update.message().text().toUpperCase().startsWith("/SETDISPATCH")) {
			this.dispatchChatId = update.message().chat().id();
			try { java.nio.file.Files.write(dispatchFile.toPath(), String.valueOf(this.dispatchChatId).getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception e) {}
			this.sendMessage(update.message().chat().id(), "Dispatch group registered.");
			return;
		}
		if (update.message().text() != null && update.message().text().toUpperCase().startsWith("/PINGDISPATCH")) {
			if (this.dispatchChatId == null) {
				this.sendMessage(update.message().chat().id(), "Dispatch not set. Use /setdispatch in the drivers group or set DISPATCH_CHAT_ID.");
			} else {
				notifyGroup("Dispatch ping from chat " + update.message().chat().id());
				this.sendMessage(update.message().chat().id(), "Ping sent to drivers group.");
			}
			return;
		}
		
		// is start conversation
		if (update.message().text() != null && (update.message().text().toUpperCase().equals("CANCEL") || update.message().text().toUpperCase().equals("/CANCEL"))) {
			this.cleanStates();
		} else if (this.isRequestRide == false && update.message().text() != null) {
			setController(new RideController(rideModel, this));
			this.isRequestRide = false;
			if (update.message().text().equals("TestApi")) {
				this.sendMessage(update.message().chat().id(), "API test");
				
			// todo upgrade text message initilize conversations
			} else if (
				update.message().text().toUpperCase().equals("HELLO") || 
				update.message().text().toUpperCase().equals("HI") ||
				update.message().text().toUpperCase().startsWith("/START") ||
				update.message().text().toUpperCase().equals("START")
			){ 	
				
				Keyboard keyboard = new ReplyKeyboardMarkup(
				        new KeyboardButton[] {
				                new KeyboardButton("Yes, let's go"),
				                new KeyboardButton("No")
			                }
				).oneTimeKeyboard(true);                
				messageText = "Hello " + update.message().from().firstName() + ", ready to ride?";
				this.sendMessageWithKeyBoard(
					update.message().chat().id(), 
					messageText, 
					keyboard
				);
				
			} else if (update.message().text().equals("Yes, let's go")) {
				this.sendMessage(update.message().chat().id(), "Please send your pickup location");
				this.isRequestRide = true;
			}
			
		// is get locations users
		} else if (this.isRequestRide && (this.locationFinish == null || this.locationStart == null)) {
			
			if (update.message().location() != null) {
				
				if (this.hasStartLocation == false) {
			
					this.locationStart = update.message().location();
					messageText = "Great! Now please send your destination";
					this.sendMessage(update.message().chat().id(), messageText);	
					this.hasStartLocation = true;				
					
				} else {
					this.locationFinish = update.message().location();
					messageText = "Searching nearby options...";
					this.sendMessage(update.message().chat().id(), messageText);
					listProductFare = this.controller.findAllProducts(this.locationStart, this.locationFinish);
					
					if (listProductFare.isEmpty()) {
						messageText = "No transport options available in your area.";
						this.sendMessage(update.message().chat().id(), messageText);	
						this.cleanStates();
					} else {
						
				        messageText = "";
				        KeyboardButton buttons[] = new KeyboardButton[listProductFare.size()];

				        for (int i = 0; i < listProductFare.size(); ++i) {
				        	messageText += listProductFare.get(i).getProduct().getDisplayName() + " ";
				        	messageText += listProductFare.get(i).getRideEstimate().getFare().getDisplay() + "\n";
				        	messageText += "\n";
				        	buttons[i] = new KeyboardButton(listProductFare.get(i).getProduct().getDisplayName());
				        }

				        Keyboard keyboard = new ReplyKeyboardMarkup(buttons).oneTimeKeyboard(true);
			        	this.sendMessageWithKeyBoard(update.message().chat().id(), messageText, keyboard);
						
					}
				
				}
				
	
			} else if (update.message().text() != null) {
				
				messageText = "I didn't understand. We need your location";
				if (this.hasStartLocation == false) {
					messageText += " to start";
				} else {
					messageText += " for the destination";
				}
				this.sendMessage(update.message().chat().id(), messageText);
			}
			
		// is working uber api
		} else if (this.onTheRun == false && update.message().text() != null && this.locationFinish != null && this.locationStart != null) {
			setController(new RideController(rideModel, this));
			
			for (ProductFare productFare : listProductFare) {
				if (update.message().text().equals(productFare.getProduct().getDisplayName())) {
					this.myRide = this.controller.request(update, productFare);
					ActivityLog.getInstance().setCurrentRide(update.message().chat().id(), this.myRide);
					this.sendMessage(update.message().chat().id(), "Processing. Type 'status' to view your ride status!");
					String groupMsg = "New ride request\n"
						+ "Passenger: " + update.message().from().firstName() + "\n"
						+ "Product: " + productFare.getProduct().getDisplayName() + "\n"
						+ "Fare: " + productFare.getRideEstimate().getFare().getDisplay() + "\n"
						+ "Pickup: " + this.locationStart.latitude() + ", " + this.locationStart.longitude() + "\n"
						+ "Dropoff: " + this.locationFinish.latitude() + ", " + this.locationFinish.longitude();
					notifyGroup(groupMsg);
					notifyGroupPickup(this.locationStart.latitude(), this.locationStart.longitude());
					this.onTheRun = true;
				}
			}
		} else if (this.onTheRun && this.myRide != null) {
			this.myRide = this.controller.statusForRide(update, this.myRide);
			messageText = "Your ride status - " + this.myRide.getStatus();
			this.sendMessage(update.message().chat().id(), messageText);
			ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "status", this.myRide.getStatus());
			String s = "Ride status update: " + this.myRide.getStatus();
			if (this.myRide.getProduct() != null) s += " • " + this.myRide.getProduct().getDisplayName();
			if (this.myRide.getDriverName() != null) s += " • Driver: " + this.myRide.getDriverName();
			notifyGroup(s);
			
			if (this.myRide.getStatus().equalsIgnoreCase("completed")) {
				this.sendMessage(update.message().chat().id(), "See you soon. Thanks for riding with us.");
				this.cleanStates();
			}
		}
		
	}
	
	public void cleanStates() {
		this.isRequestRide = false;
		this.hasStartLocation = false;
		this.locationStart = null;
		this.locationFinish = null;
		this.myRide = null;
		this.onTheRun = false;
	}
	
	public void update(long chatId, String data){
		sendResponse = bot.execute(new SendMessage(chatId, data));
	}
	
	public void sendMessage(Long long1, String message) {	
		SendMessage sendMessage = new SendMessage(long1, message);
		bot.execute(sendMessage);
	}
	
	public void sendMessageWithKeyBoard(Long long1, String message, Keyboard keyboard) {
		SendMessage sendMessage = new SendMessage(long1, message);
		sendMessage.replyMarkup(keyboard);
		bot.execute(sendMessage);
	}
	
	
	public void sendTypingMessage(Update update){
		baseResponse = bot.execute(new SendChatAction(update.message().chat().id(), ChatAction.typing.name()));
	}
	
	public void notifyGroup(String message) {
		if (bot != null && dispatchChatId != null) {
			try {
				BaseResponse r = bot.execute(new SendMessage(dispatchChatId, message));
				if (r == null || !r.isOk()) {
					System.out.println("Group notify failed");
				}
			} catch (Exception e) {
				System.out.println("Failed to notify group: " + e.getMessage());
			}
		}
	}
	
	public void notifyGroupPickup(double lat, double lon) {
		if (bot != null && dispatchChatId != null) {
			try {
				String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
				InlineKeyboardButton mapBtn = new InlineKeyboardButton("📍 Map").url(mapsUrl);
				InlineKeyboardMarkup markup = new InlineKeyboardMarkup(new InlineKeyboardButton[]{mapBtn});
				SendMessage msg = new SendMessage(dispatchChatId, "Pickup location:");
				msg.replyMarkup(markup);
				bot.execute(msg);
				bot.execute(new SendLocation(dispatchChatId, (float) lat, (float) lon));
			} catch (Exception e) {
				System.out.println("Failed to send pickup map: " + e.getMessage());
			}
		}
	}
	
}
