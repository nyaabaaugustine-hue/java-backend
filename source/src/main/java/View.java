import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.Message;
// import removed in newer versions
import com.pengrad.telegrambot.model.Location;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendLocation;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
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

    // State machine for ride flow
    enum RideState {
        IDLE,
        WAITING_PICKUP,
        WAITING_DESTINATION,
        WAITING_VEHICLE_TYPE,
        SHOWING_RIDES,
        WAITING_PHONE,
        CONFIRMED,
        ON_TRIP
    }
    
    private TelegramBot bot;
    private Long dispatchChatId;
    private Long testDriverChatId;
    private java.nio.file.Path dispatchFile;
    private RideModel rideModel;
    private RideController controller;
    private RideState rideState = RideState.IDLE;
    private com.pengrad.telegrambot.model.Location locationStart;
    private com.pengrad.telegrambot.model.Location locationFinish;
    private boolean hasClientPhone = false;
    private String clientPhone;
    // Legacy boolean flags for backward compatibility during refactor
    private boolean isRequestRide = false;
    private boolean hasStartLocation = false;
    private boolean awaitingVehicleType = false;
    private boolean awaitingPhoneConfirmation = false;
    private String clientName;
    private String selectedVehicleType = null;
    private boolean isProcessingRequest = false; // Prevent duplicate processing
    private long lastProcessedMessageId = 0; // Track last processed message
    private TransportRide myRide = null; // Assuming this exists elsewhere in your code
    private boolean onTheRun = false;
    private List<String> lockedRides = new java.util.ArrayList<>();
    private GetUpdatesResponse updatesResponse = null;
    private int queuesIndex = 0;
    private SendResponse sendResponse = null;
    private BaseResponse baseResponse = null;
    
    // List to store product fares
    private List<ProductFare> listProductFare = new java.util.ArrayList<>();
    
    // Auto-driver registration system
    private Map<Long, AutoDriverInfo> autoRegisteredDrivers;
    
    /**
     * Generate local pricing options without external API calls
     * Uses Uber-like pricing model with base fare + distance calculation
     */
    private List<ProductFare> generateLocalPricingOptions() {
        List<ProductFare> options = new java.util.ArrayList<>();
        
        // Debug: Check if location data is available
        if (this.locationStart == null || this.locationFinish == null) {
            System.err.println("ERROR: Location data missing for pricing calculation");
            System.err.println("locationStart: " + (this.locationStart != null ? "present" : "null"));
            System.err.println("locationFinish: " + (this.locationFinish != null ? "present" : "null"));
            return options;
        }
        
        System.out.println("Generating local pricing options...");
        System.out.println("Pickup: " + this.locationStart.latitude() + ", " + this.locationStart.longitude());
        System.out.println("Destination: " + this.locationFinish.latitude() + ", " + this.locationFinish.longitude());
        
        // Calculate distance between pickup and destination
        double distanceKm = calculateDistance(
            this.locationStart.latitude(), this.locationStart.longitude(),
            this.locationFinish.latitude(), this.locationFinish.longitude()
        );
        
        System.out.println("Calculated distance: " + String.format("%.2f", distanceKm) + " km");
        
        // Define vehicle types with Uber-style pricing
        String[][] vehicleTypes = {
            {"🚗 Standard", "taxi", "5.0", "2.0"},  // Base: GHS 5, Per km: GHS 2
            {"🚙 XL", "xl", "8.0", "2.5"},          // Base: GHS 8, Per km: GHS 2.5
            {"🚕 Premium", "premium", "12.0", "3.0"} // Base: GHS 12, Per km: GHS 3
        };
        
        for (String[] vehicle : vehicleTypes) {
            String displayName = vehicle[0];
            String productId = vehicle[1];
            double baseFare = Double.parseDouble(vehicle[2]);
            double perKmRate = Double.parseDouble(vehicle[3]);
            
            // Calculate fare: base + (distance × rate)
            double totalPrice = baseFare + (distanceKm * perKmRate);
            
            // Apply dynamic pricing factor (simulate demand)
            double demandFactor = 1.0 + (Math.random() * 0.3); // 0-30% surge
            totalPrice *= demandFactor;
            
            // Round to 2 decimal places
            totalPrice = Math.round(totalPrice * 100.0) / 100.0;
            
            // Create product
            TransportProduct product = new TransportProduct(
                productId,
                displayName.replace("🚗 ", "").replace("🚙 ", "").replace("🚕 ", ""),
                displayName + " ride",
                productId.equals("taxi") ? 4 : (productId.equals("xl") ? 6 : 4)
            );
            
            // Create fare
            String priceStr = String.format("₵%.2f", totalPrice);
            TransportFare fare = new TransportFare(
                java.util.UUID.randomUUID().toString(),
                String.valueOf(totalPrice),
                "₵",
                priceStr
            );
            
            // Create estimate
            TransportEstimate estimate = new TransportEstimate(
                product,
                fare,
                3, // pickup duration in minutes
                Math.max(5, (int)(distanceKm * 2)) // trip duration estimate
            );
            
            // Create product fare
            ProductFare productFare = new ProductFare();
            productFare.setProduct(product);
            productFare.setRideEstimate(estimate);
            
            options.add(productFare);
            
            System.out.println(displayName + ": " + priceStr + " for " + 
                String.format("%.2f", distanceKm) + " km");
        }
        
        return options;
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // Earth's radius in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
    
    public View(TelegramBot bot) {
        this.bot = bot;
        this.dispatchFile = java.nio.file.Paths.get("dispatch_chat_id.txt");
        this.autoRegisteredDrivers = new java.util.concurrent.ConcurrentHashMap<>();
        
        // Load dispatch chat ID if exists
        try {
            String envId = System.getenv("DISPATCH_CHAT_ID");
            if (envId != null && envId.trim().length() > 0) {
                try {
                    this.dispatchChatId = Long.parseLong(envId.trim());
                    System.out.println("Loaded dispatch chat ID from env: " + this.dispatchChatId);
                } catch (Exception e) {
                }
            }
            String envTestId = System.getenv("DISPATCH_TEST_DRIVER_CHAT_ID");
            if (envTestId != null && envTestId.trim().length() > 0) {
                try {
                    this.testDriverChatId = Long.parseLong(envTestId.trim());
                    System.out.println("Loaded test driver chat ID from env: " + this.testDriverChatId);
                } catch (Exception e) {
                }
            }
            if (this.dispatchChatId == null || this.testDriverChatId == null) {
                try {
                    java.io.File f1 = new java.io.File(".env");
                    if (f1.exists()) {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(f1.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String l = line.trim();
                            if (this.dispatchChatId == null && l.startsWith("DISPATCH_CHAT_ID=")) {
                                String[] parts = l.split("=", 2);
                                if (parts.length == 2) {
                                    try { this.dispatchChatId = Long.parseLong(parts[1].trim()); System.out.println("Loaded dispatch chat ID from .env: " + this.dispatchChatId); } catch (Exception ex) {}
                                }
                            } else if (this.testDriverChatId == null && l.startsWith("DISPATCH_TEST_DRIVER_CHAT_ID=")) {
                                String[] parts = l.split("=", 2);
                                if (parts.length == 2) {
                                    try { this.testDriverChatId = Long.parseLong(parts[1].trim()); System.out.println("Loaded test driver chat ID from .env: " + this.testDriverChatId); } catch (Exception ex) {}
                                }
                            }
                        }
                    }
                } catch (Exception e2) {}
            }
            if (this.dispatchChatId == null || this.testDriverChatId == null) {
                try {
                    java.io.File f2 = new java.io.File(".env.production");
                    if (f2.exists()) {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(f2.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String l = line.trim();
                            if (this.dispatchChatId == null && l.startsWith("DISPATCH_CHAT_ID=")) {
                                String[] parts = l.split("=", 2);
                                if (parts.length == 2) {
                                    try { this.dispatchChatId = Long.parseLong(parts[1].trim()); System.out.println("Loaded dispatch chat ID from .env.production: " + this.dispatchChatId); } catch (Exception ex) {}
                                }
                            } else if (this.testDriverChatId == null && l.startsWith("DISPATCH_TEST_DRIVER_CHAT_ID=")) {
                                String[] parts = l.split("=", 2);
                                if (parts.length == 2) {
                                    try { this.testDriverChatId = Long.parseLong(parts[1].trim()); System.out.println("Loaded test driver chat ID from .env.production: " + this.testDriverChatId); } catch (Exception ex) {}
                                }
                            }
                        }
                    }
                } catch (Exception e3) {}
            }
            if (java.nio.file.Files.exists(dispatchFile)) {
                String content = new String(java.nio.file.Files.readAllBytes(dispatchFile), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    Long fileId = Long.parseLong(content.trim());
                    this.dispatchChatId = fileId;
                    System.out.println("Loaded dispatch chat ID: " + this.dispatchChatId);
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load dispatch chat ID: " + e.getMessage());
        }
    }
    
    public void setController(RideController controller){ //Strategy Pattern
        this.controller = controller;
    }
    
    public void setRideModel(RideModel model) {
        this.rideModel = model;
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
            
            for (Update update : updates) {
                try {
                    if (update.message() != null && update.message().text() != null) {
                        String txt = update.message().text().trim();
                        if (txt.equalsIgnoreCase("/setdispatch")) {
                            this.dispatchChatId = update.message().chat().id();
                            try {
                                java.nio.file.Files.write(dispatchFile, String.valueOf(this.dispatchChatId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            } catch (Exception w) {}
                            try { bot.execute(new SendMessage(update.message().chat().id(), "Dispatch chat set • " + this.dispatchChatId)); } catch (Exception e) {}
                            int nextOffsetSet = update.updateId() + 1;
                            if (nextOffsetSet > queuesIndex) queuesIndex = nextOffsetSet;
                            continue;
                        } else if (txt.equalsIgnoreCase("/settestdriver")) {
                            this.testDriverChatId = update.message().chat().id();
                            try { bot.execute(new SendMessage(update.message().chat().id(), "Test driver chat set • " + this.testDriverChatId)); } catch (Exception e) {}
                            int nextOffsetSet2 = update.updateId() + 1;
                            if (nextOffsetSet2 > queuesIndex) queuesIndex = nextOffsetSet2;
                            continue;
                        } else if (txt.equalsIgnoreCase("/myid")) {
                            Long id = update.message().chat().id();
                            try { bot.execute(new SendMessage(update.message().chat().id(), "Your chat ID • " + id)); } catch (Exception e) {}
                            int nextOffsetId = update.updateId() + 1;
                            if (nextOffsetId > queuesIndex) queuesIndex = nextOffsetId;
                            continue;
                        } else if (txt.equalsIgnoreCase("/groupid")) {
                            Long id = update.message().chat().id();
                            String type = update.message().chat().type() != null ? update.message().chat().type().name() : "unknown";
                            String title = update.message().chat().title() != null ? update.message().chat().title() : "";
                            String msg = "Chat ID • " + id + "\nType • " + type + (title.length() > 0 ? "\nTitle • " + title : "");
                            try { bot.execute(new SendMessage(update.message().chat().id(), msg)); } catch (Exception e) {}
                            int nextOffsetGid = update.updateId() + 1;
                            if (nextOffsetGid > queuesIndex) queuesIndex = nextOffsetGid;
                            continue;
                        } else if (txt.equalsIgnoreCase("/testdispatch")) {
                            Long target = this.dispatchChatId != null ? this.dispatchChatId : update.message().chat().id();
                            
                            // ENHANCED ACCEPT BUTTON - BIGGER, BOLDER, MORE VISIBLE
                            InlineKeyboardButton acceptBtn = new InlineKeyboardButton("🟢 ✅ ACCEPT RIDE NOW").callbackData("ACCEPT::" + update.message().chat().id());
                            
                            // ENHANCED DECLINE BUTTON - BIGGER, BOLDER, MORE VISIBLE
                            InlineKeyboardButton declineBtn = new InlineKeyboardButton("🔴 ❌ DECLINE RIDE").callbackData("DECLINE:");
                            
                            // ARRANGE BUTTONS VERTICALLY FOR MAXIMUM VISIBILITY
                            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                                new InlineKeyboardButton[][]{
                                    {acceptBtn},
                                    {declineBtn}
                                }
                            );
                            
                            // ENHANCED MESSAGE WITH BETTER FORMATTING AND SPACING
                            String m = "🚨🚨🚨 <b>NEW RIDE REQUEST</b> 🚨🚨🚨\n\n"
                                + "👤 <b>Passenger:</b> Test User\n\n"
                                + "🚗 <b>Vehicle Type:</b> Standard\n\n"
                                + "💰 <b>Estimated Fare:</b> ₵10.00\n\n"
                                + "📍 <b>Status:</b> Waiting for driver\n\n"
                                + "👇 <b>PLEASE CHOOSE BELOW:</b>";
                            
                            SendMessage msg = new SendMessage(target, m);
                            msg.parseMode(ParseMode.HTML);
                            msg.disableWebPagePreview(true);
                            msg.replyMarkup(markup);
                            try { bot.execute(msg); } catch (Exception e) {}
                            int nextOffsetTest = update.updateId() + 1;
                            if (nextOffsetTest > queuesIndex) queuesIndex = nextOffsetTest;
                            continue;
                        }
                    }
                } catch (Exception e) {}
                if (update.callbackQuery() != null && update.callbackQuery().data() != null) {
                    String data = update.callbackQuery().data();
                    try {
                        if (data.startsWith("ACCEPT:")) {
                            String[] parts = data.split(":", 3);
                            String rideId = parts.length > 1 ? parts[1] : null;
                            String passengerChatStr = parts.length > 2 ? parts[2] : null;
                            handleAccept(update, rideId, passengerChatStr);
                            try { bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()).text("Accepted")); } catch (Exception e) {}
                        } else if (data.startsWith("DECLINE:")) {
                            String[] parts = data.split(":", 2);
                            String rideId = parts.length > 1 ? parts[1] : null;
                            // Add logic to handle decline if needed
                            try { bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()).text("Declined")); } catch (Exception e) {}
                        }
                    } catch (Exception e) {
                        try { bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()).text("Error")); } catch (Exception ex) {}
                    }
                    continue;
                }
                if (update.message() != null && update.message().text() != null) {
                    System.out.println("Text update: chat " + update.message().chat().id() + " text=" + update.message().text());
                } else {
                    System.out.println("Non-text update received: id=" + update.updateId());
                }
                                if (update.message() == null) {
                    // For non-message updates (like callback queries), offset is already advanced above
                    continue;
                }
                if (update.message().text() != null) {
                    ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "text", update.message().text());
                } else if (update.message().location() != null) {
                    ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "location", update.message().location().latitude()+" , "+update.message().location().longitude());
                } else if (update.message().contact() != null) {
                    clientPhone = update.message().contact().phoneNumber();
                    hasClientPhone = clientPhone != null && clientPhone.length() > 0;
                    ActivityLog.getInstance().addEvent(String.valueOf(update.message().chat().id()), "phone", hasClientPhone ? clientPhone : "");
                }
                this.buildInteraction(update);
                
                // Advance offset after processing the update to prevent re-processing
                int nextOffset = update.updateId() + 1;
                if (nextOffset > queuesIndex) {
                    queuesIndex = nextOffset;
                }
				
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
		processAutoDriverRegistration(update);

		if (update.message() == null) {
			return;
		}

		if (update.message().messageId() <= this.lastProcessedMessageId) {
			System.out.println("DEBUG: Skipping duplicate message ID: " + update.message().messageId());
			return;
		}

		if (this.isProcessingRequest) {
			System.out.println("DEBUG: Bot is busy processing another request");
			this.sendMessage(update.message().chat().id(), "⏳ Processing your previous request... Please wait.");
			return;
		}

		this.isProcessingRequest = true;
		this.lastProcessedMessageId = update.message().messageId();

		try {
			if (update.message().text() != null) {
				String text = update.message().text();
				if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("start")) {
					String chatId = String.valueOf(update.message().chat().id());
					String fullName = update.message().from() != null ? update.message().from().firstName() : "Passenger";
					startRideFlow(update, fullName, chatId);
					return;
				}
			}

			switch (this.rideState) {
			case IDLE:
				handleIdleState(update);
				return;

			case WAITING_PICKUP:
				handlePickupState(update);
				return;

			case WAITING_DESTINATION:
				handleDestinationState(update);
				return;

			case WAITING_VEHICLE_TYPE:
				handleVehicleTypeState(update);
				return;

			case SHOWING_RIDES:
				handleShowingRidesState(update);
				return;

			case WAITING_PHONE:
				handleWaitingPhoneState(update);
				return;

			case CONFIRMED:
				handleConfirmedState(update);
				return;

			case ON_TRIP:
				handleOnTripState(update);
				return;

			default:
				handleIdleState(update);
				return;
			}
		} finally {
			this.isProcessingRequest = false;
		}
	}
	
	// STATE MACHINE HANDLERS
	
	/**
	 * Start the ride flow with atomic state reset
	 */
	private void startRideFlow(Update update, String fullName, String chatId) {
	    System.out.println("DEBUG: Processing /start command, calling cleanStates()");
	    this.cleanStates();
	    System.out.println("DEBUG: After cleanStates() - rideState=" + this.rideState);
	    ActivityLog.getInstance().addEvent(chatId, "SESSION_START", 
	        "User initiated session: " + fullName);
	    Keyboard keyboard = new ReplyKeyboardMarkup(
	            new KeyboardButton[] {
	                    new KeyboardButton("Yes, let's go"),
	                    new KeyboardButton("No")
	            }
	    ).oneTimeKeyboard(true);                
	    String messageText = "Hello " + update.message().from().firstName() + ", ready to ride?";
	    System.out.println("Processing /start for chat " + update.message().chat().id());
	    this.sendMessageWithKeyBoard(
	        update.message().chat().id(), 
	        messageText, 
	        keyboard
	    );
	}
	
	/**
	 * Handle idle state - waiting for user to start ride
	 */
	private void handleIdleState(Update update) {
	    if (update.message().text() == null) return;
	    
	    setController(new RideController(rideModel, this));
	    
	    if (update.message().text().equals("TestApi")) {
	        this.sendMessage(update.message().chat().id(), "API test");
	        return;
	    }
	    
	    if (update.message().text().equals("Yes, let's go")) {
	        System.out.println("DEBUG: Processing 'Yes, let's go' message in IDLE state");
	        
	        // Send typing indicator to show bot is processing
	        this.sendTypingMessage(update);
	        
	        // Atomic state reset for new ride request
	        this.locationStart = null;
	        this.locationFinish = null;
	        this.listProductFare.clear();
	        
	        // Create keyboard with location sharing button
	        KeyboardButton locationButton = new KeyboardButton("📍 Share Location");
	        locationButton.requestLocation(true);
	        
	        Keyboard keyboard = new ReplyKeyboardMarkup(locationButton).oneTimeKeyboard(false);
	        this.sendMessageWithKeyBoard(update.message().chat().id(), 
	            "Please share your pickup location by clicking the button below or sending your location manually (Allow Location).\n\n💡 Tip: Sharing your phone number helps drivers contact you if needed!", 
	            keyboard);
	        
	        this.rideState = RideState.WAITING_PICKUP;
	        return;
	    }
	    
	    // Handle greetings
	    String upperText = update.message().text().toUpperCase();
	    if (upperText.equals("HELLO") || upperText.equals("HI") || upperText.equals("START")) {
	        Keyboard keyboard = new ReplyKeyboardMarkup(
	                new KeyboardButton[] {
	                        new KeyboardButton("Yes, let's go"),
	                        new KeyboardButton("No")
	                }
	        ).oneTimeKeyboard(false);
	        
	        String messageText = "Hello " + update.message().from().firstName() + ", ready to ride?\n\n📱 During booking, you'll be asked to share your phone number so drivers can contact you if needed.";
	        this.sendMessageWithKeyBoard(update.message().chat().id(), messageText, keyboard);
	        return;
	    }
	    
	    // Default response for idle state
	    this.sendMessage(update.message().chat().id(), "Send /start to begin a ride request.");
	}
	
	/**
	 * Handle pickup location collection
	 */
	private void handlePickupState(Update update) {
	    if (update.message().location() != null) {
	        this.locationStart = update.message().location();
	        this.rideState = RideState.WAITING_DESTINATION;
	        
	        ActivityLog.getInstance().addEvent(
	            String.valueOf(update.message().chat().id()), 
	            "PICKUP_LOCATION_SET", 
	            "Pickup location set: " + this.locationStart.latitude() + ", " + this.locationStart.longitude()
	        );
	        
	        this.sendMessage(update.message().chat().id(), "Great! Now please send your destination");
	        return;
	    }
	    
	    // Handle text input during pickup phase
	    if (update.message().text() != null) {
	        String userText = update.message().text().toUpperCase();
	        if (userText.equals("CANCEL") || userText.equals("STOP") || userText.equals("BACK")) {
	            this.sendMessage(update.message().chat().id(), "Request canceled. Send /start to begin a new ride request.");
	            this.cleanStates();
	            return;
	        }
	        
	        // Check if user might be trying to send phone number
	        String userTextInput = update.message().text();
	        String cleanInput = userTextInput.replaceAll("[\\s\\-\\(\\)]", "");
	        
	        // If input looks like a phone number, guide user to correct stage
	        if (cleanInput.matches("^[0-9+]{8,15}$")) {
	            this.sendMessage(update.message().chat().id(), 
	                "I see you're trying to share a phone number. We'll ask for your phone number after you complete these steps: 1) Share pickup location, 2) Share destination, 3) Select vehicle type, 4) Choose ride option, 5) Share phone number. Please share your pickup location first:");
	        } else {
	            // Generic location reminder
	            this.sendMessage(update.message().chat().id(), 
	                "I need your pickup location to continue. Please use the location sharing button below or send your location manually.");
	        }
	        
	        // Show location sharing button
	        KeyboardButton locationButton = new KeyboardButton("Share Location");
	        locationButton.requestLocation(true);
	        Keyboard keyboard = new ReplyKeyboardMarkup(locationButton).oneTimeKeyboard(false);
	        this.sendMessageWithKeyBoard(update.message().chat().id(), "", keyboard);
	        return;
	    }
	}
	
	/**
	 * Handle destination location collection
	 */
	private void handleDestinationState(Update update) {
	    if (update.message().location() != null) {
	        this.locationFinish = update.message().location();
	        this.rideState = RideState.WAITING_VEHICLE_TYPE;
	        
	        ActivityLog.getInstance().addEvent(
	            String.valueOf(update.message().chat().id()), 
	            "DESTINATION_LOCATION_SET", 
	            "Destination location set: " + this.locationFinish.latitude() + ", " + this.locationFinish.longitude()
	        );
	        
	        // Ask for vehicle type
	        KeyboardButton[] buttons = {
	            new KeyboardButton("🚗 Standard"),
	            new KeyboardButton("🚙 XL"),
	            new KeyboardButton("🚕 Premium")
	        };
	        
	        Keyboard keyboard = new ReplyKeyboardMarkup(buttons).oneTimeKeyboard(true);
	        this.sendMessageWithKeyBoard(update.message().chat().id(), 
	            "What type of vehicle would you prefer?", 
	            keyboard);
	        return;
	    }
	    
	    // Handle text input during destination phase
	    if (update.message().text() != null) {
	        String userText = update.message().text().toUpperCase();
	        if (userText.equals("CANCEL") || userText.equals("STOP") || userText.equals("BACK")) {
	            this.sendMessage(update.message().chat().id(), "Request canceled. Send /start to begin a new ride request.");
	            this.cleanStates();
	            return;
	        }
	        
	        this.sendMessage(update.message().chat().id(), 
	            "I need your destination location. Please share your location manually or use the location button.");
	        return;
	    }
	}
	
	/**
	 * Handle vehicle type selection
	 */
	private void handleVehicleTypeState(Update update) {
	    if (update.message().text() == null) return;
	    
	    String vehicleChoice = update.message().text();
	    if (vehicleChoice.equals("🚗 Standard") || vehicleChoice.equals("🚙 XL") || vehicleChoice.equals("🚕 Premium")) {
	        this.selectedVehicleType = vehicleChoice;
	        this.sendTypingMessage(update);
	        
	        // Generate ride options
	        this.listProductFare = generateLocalPricingOptions();
	        
	        if (this.listProductFare.isEmpty()) {
	            this.sendMessage(update.message().chat().id(), "No transport options available in your area.");
	            this.cleanStates();
	            return;
	        }
	        
	        // Show ride options
	        StringBuilder message = new StringBuilder("📋 Available ride options:\n\n");
	        KeyboardButton[] buttons = new KeyboardButton[this.listProductFare.size()];
	        
	        for (int i = 0; i < this.listProductFare.size(); i++) {
	            ProductFare fare = this.listProductFare.get(i);
	            message.append((i + 1)).append(". ").append(fare.getProduct().getDisplayName()).append("\n");
	            message.append("   💰 Price: ").append(fare.getRideEstimate().getFare().getDisplay()).append("\n\n");
	            buttons[i] = new KeyboardButton(fare.getProduct().getDisplayName());
	        }
	        
	        message.append("\nPlease select your preferred option.");
	        
	        Keyboard keyboard = new ReplyKeyboardMarkup(buttons).oneTimeKeyboard(true);
	        this.sendMessageWithKeyBoard(update.message().chat().id(), message.toString(), keyboard);
	        
	        this.rideState = RideState.SHOWING_RIDES;
	        return;
	    }
	    
	    // Invalid vehicle type
	    this.sendMessage(update.message().chat().id(), "Please select a valid vehicle type from the options provided.");
	}
	
	/**
	 * Handle ride option selection
	 */
	private void handleShowingRidesState(Update update) {
	    if (update.message().text() == null) return;
	    
	    // Check if user selected a valid ride option
	    for (ProductFare productFare : this.listProductFare) {
	        if (update.message().text().equals(productFare.getProduct().getDisplayName())) {
	            this.selectedVehicleType = productFare.getProduct().getDisplayName();
	            
	            // Now ask for phone number (LAST QUESTION)
	            KeyboardButton contactButton = new KeyboardButton("📞 Share Phone Number");
	            contactButton.requestContact(true);
	            Keyboard keyboard = new ReplyKeyboardMarkup(
	                new KeyboardButton[] {contactButton, new KeyboardButton("Skip")}
	            ).oneTimeKeyboard(false);
	            
	            String messageText = "✅ Great choice! " + productFare.getProduct().getDisplayName() + 
	                " selected for " + productFare.getRideEstimate().getFare().getDisplay() + 
	                "\n\n📲 Please share your phone number so the driver can contact you, or tap 'Skip' to continue without sharing.";
	            
	            this.sendMessageWithKeyBoard(update.message().chat().id(), messageText, keyboard);
	            
	            this.rideState = RideState.WAITING_PHONE;
	            return;
	        }
	    }
	    
	    // Invalid ride selection
	    this.sendMessage(update.message().chat().id(), "I didn't understand that option. Please select a ride option from the list above.");
	}
	
	/**
	 * Handle phone number collection (LAST QUESTION)
	 */
	private void handleWaitingPhoneState(Update update) {
	    // Handle contact sharing
	    if (update.message().contact() != null) {
	        this.clientPhone = update.message().contact().phoneNumber();
	        this.hasClientPhone = this.clientPhone != null && this.clientPhone.length() > 0;
	        
	        this.processRideBooking(update);
	        return;
	    }
	    
	    // Handle text input
	    if (update.message().text() != null) {
	        String text = update.message().text().toUpperCase();
	        
	        if (text.equals("SKIP")) {
	            this.processRideBooking(update);
	            return;
	        }
	        
	        // Check if user sent phone number as text
	        String cleanPhone = text.replaceAll("[\\s\\-\\(\\)]", "");
	        if (cleanPhone.matches("^[0-9+]{10,15}$")) {
	            this.clientPhone = cleanPhone;
	            this.hasClientPhone = true;
	            this.processRideBooking(update);
	            return;
	        }
	        
	        // Invalid input
	        this.sendMessage(update.message().chat().id(), 
	            "Please share your phone number using the button, type it directly, or reply 'Skip' to continue without sharing.");
	    }
	}
	
	/**
	 * Process the final ride booking
	 */
	private void processRideBooking(Update update) {
	    String chatId = String.valueOf(update.message().chat().id());
	    String firstName = update.message().from() != null ? update.message().from().firstName() : "Unknown";
	    
	    // Find selected product fare
	    ProductFare selectedFare = null;
	    for (ProductFare fare : this.listProductFare) {
	        if (fare.getProduct().getDisplayName().equals(this.selectedVehicleType)) {
	            selectedFare = fare;
	            break;
	        }
	    }
	    
	    if (selectedFare == null) {
	        this.sendMessage(update.message().chat().id(), "Error processing ride request. Please start over with /start");
	        this.cleanStates();
	        return;
	    }
	    
	    // Log the booking
	    ActivityLog.getInstance().addEvent(chatId, "RIDE_BOOKED", 
	        "Ride booked | User: " + firstName + 
	        " | Product: " + selectedFare.getProduct().getDisplayName() + 
	        " | Fare: " + selectedFare.getRideEstimate().getFare().getDisplay() +
	        " | Pickup: " + this.locationStart.latitude() + ", " + this.locationStart.longitude() +
	        " | Dropoff: " + this.locationFinish.latitude() + ", " + this.locationFinish.longitude()
	        + (this.hasClientPhone ? " | Phone: " + this.clientPhone : " | Phone: Not provided"));
	    
	    // Process the ride request
	    this.myRide = this.controller.request(update, selectedFare);
	    try { 
	        if (this.myRide != null) 
	            this.myRide.setFareDisplay(selectedFare.getRideEstimate().getFare().getDisplay()); 
	    } catch (Exception e) {}
	    
	    try { 
	        if (this.myRide != null && this.hasClientPhone) 
	            this.myRide.setPassengerPhone(this.clientPhone); 
	    } catch (Exception e) {}
	    
	    ActivityLog.getInstance().setCurrentRide(update.message().chat().id(), this.myRide);
	    
	    KeyboardButton statusButton = new KeyboardButton("Status");
	    KeyboardButton skipButton = new KeyboardButton("Skip");
	    Keyboard keyboard = new ReplyKeyboardMarkup(new KeyboardButton[]{statusButton, skipButton}).oneTimeKeyboard(false);
	    String confirmMessage = "🚀 Ride request processed!\n\n" +
	        "Tap 'Status' to check your ride now, or 'Skip' to close this menu.\n" +
	        "You can type 'status' anytime to check later.";
	    this.sendMessageWithKeyBoard(update.message().chat().id(), confirmMessage, keyboard);
	    
        // Auto-assign a driver immediately
        try {
            autoAssignDriverToRide(update.message().chat().id());
        } catch (Exception e) {}

        // ENHANCED Notify drivers group with better formatting
        String groupMsg = "🚨🚨🚨 <b>NEW RIDE REQUEST</b> 🚨🚨🚨\n\n"
            + "👤 <b>Passenger:</b> " + firstName + "\n\n"
            + "🚗 <b>Vehicle Type:</b> " + selectedFare.getProduct().getDisplayName() + "\n\n"
            + "💰 <b>Estimated Fare:</b> " + selectedFare.getRideEstimate().getFare().getDisplay() + "\n\n"
            + "🟢 <b>Accept Button Below</b> 👇\n\n"
            + "🔴 <b>Decline Button Below</b> 👇\n\n"
            + "📍 <b>Pickup Location:</b> See map below";
	        
	    notifyGroupRequest(groupMsg, this.myRide != null ? this.myRide.getRideId() : null, 
	        update.message().chat().id(), this.hasClientPhone ? this.clientPhone : null);
	        
	    if (this.hasClientPhone) {
	        notifyGroupCall(this.clientPhone);
	    }
	    
	    notifyGroupPickup(this.locationStart.latitude(), this.locationStart.longitude());
	    this.onTheRun = true;
	    this.rideState = RideState.ON_TRIP;
	}
	
	/**
	 * Handle confirmed/ongoing ride state
	 */
	private void handleConfirmedState(Update update) {
	    // This state should transition to ON_TRIP immediately after booking
	    this.rideState = RideState.ON_TRIP;
	    handleOnTripState(update);
	}
	
	/**
	 * Handle ongoing trip status updates
	 */
	private void handleOnTripState(Update update) {
	    if (this.myRide == null) {
	        this.sendMessage(update.message().chat().id(), "No active ride found. Send /start to book a new ride.");
	        this.cleanStates();
	        return;
	    }
	    
	    if (update.message().text() != null) {
	        String text = update.message().text();
	        String lower = text.toLowerCase();
	        
	        if (lower.equals("status")) {
	            this.myRide = this.controller.statusForRide(update, this.myRide);
	            
	            String statusMessage = "Your ride status - " + this.myRide.getStatus();
	            this.sendMessage(update.message().chat().id(), statusMessage);
	            
	            ActivityLog.getInstance().addEvent(
	                String.valueOf(update.message().chat().id()), 
	                "RIDE_STATUS_UPDATE", 
	                "Ride status: " + this.myRide.getStatus() + " | Ride ID: " + this.myRide.getRideId()
	            );
	            
	            String groupMessage = "Ride status update: " + this.myRide.getStatus();
	            if (this.myRide.getProduct() != null) 
	                groupMessage += " • " + this.myRide.getProduct().getDisplayName();
	            if (this.myRide.getDriverName() != null) 
	                groupMessage += " • " + this.myRide.getDriverName();
	            
	            notifyGroup(groupMessage);
	            
	            if (this.myRide.getStatus().equalsIgnoreCase("completed")) {
	                handleRideCompletion(update);
	            }
	            return;
	        }
	        
        if (lower.equals("skip")) {
            this.sendMessage(update.message().chat().id(), "Okay, I will keep tracking your ride. You can type 'status' anytime to check.");
            return;
        }
	    }
	    
	    this.sendMessage(update.message().chat().id(), "Type 'status' to check your ride status.");
	}
	
	/**
	 * Handle ride completion and generate receipt
	 */
	private void handleRideCompletion(Update update) {
	    String trackingCode = "CYBER-" + System.currentTimeMillis();
	    
	    ActivityLog.getInstance().addEvent(
	        String.valueOf(update.message().chat().id()), 
	        "RIDE_COMPLETED", 
	        "Ride completed successfully | Ride ID: " + this.myRide.getRideId() +
	        " | Tracking Code: " + trackingCode
	    );
	    
	    // Generate receipt
	    String receiptMessage = "✅ Ride Completed!\n\n" +
	        "📄 Receipt Details:\n" +
	        "• Ride ID: " + this.myRide.getRideId() + "\n" +
	        "• Tracking Code: " + trackingCode + "\n" +
	        "• Driver: " + (this.myRide.getDriverName() != null ? this.myRide.getDriverName() : "Unknown") + "\n" +
	        "• Vehicle: " + (this.myRide.getVehicleModel() != null ? this.myRide.getVehicleModel() : "Not specified") + "\n" +
	        "• Plate: " + (this.myRide.getVehiclePlate() != null ? this.myRide.getVehiclePlate() : "Not specified") + "\n" +
	        "• Fare: " + (this.myRide.getFareDisplay() != null ? this.myRide.getFareDisplay() : "Not specified") + "\n" +
	        "• Pickup: " + this.locationStart.latitude() + ", " + this.locationStart.longitude() + "\n" +
	        "• Dropoff: " + this.locationFinish.latitude() + ", " + this.locationFinish.longitude() + "\n\n" +
	        "Thank you for choosing CyberMove! 🚗\n" +
	        "Use tracking code " + trackingCode + " for any inquiries.";
	        
	    this.sendMessage(update.message().chat().id(), receiptMessage);
	    this.cleanStates();
	}
	
	public void cleanStates() {
		this.rideState = RideState.IDLE;
		this.locationStart = null;
		this.locationFinish = null;
		this.myRide = null;
		this.onTheRun = false;
		this.clientPhone = null;
		this.hasClientPhone = false;
		this.selectedVehicleType = null;
		this.isProcessingRequest = false;
		this.lastProcessedMessageId = 0;
		this.listProductFare.clear();
		// Reset legacy flags for backward compatibility
		this.isRequestRide = false;
		this.hasStartLocation = false;
		this.awaitingVehicleType = false;
		this.awaitingPhoneConfirmation = false;
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
				if (r == null) {
					System.out.println("Group notify failed: null response");
                    if (testDriverChatId != null) {
                        try {
                            BaseResponse r2 = bot.execute(new SendMessage(testDriverChatId, "[fallback] " + message));
                            if (r2 == null || !r2.isOk()) {
                                System.out.println("Fallback notify failed");
                            } else {
                                System.out.println("Fallback notify sent to test driver chat");
                            }
                        } catch (Exception ex) {}
                    }
				} else if (!r.isOk()) {
					System.out.println("Group notify failed: code=" + r.errorCode() + " desc=" + r.description());
                    if (testDriverChatId != null) {
                        try {
                            BaseResponse r2 = bot.execute(new SendMessage(testDriverChatId, "[fallback] " + message));
                            if (r2 == null || !r2.isOk()) {
                                System.out.println("Fallback notify failed");
                            } else {
                                System.out.println("Fallback notify sent to test driver chat");
                            }
                        } catch (Exception ex) {}
                    }
				}
			} catch (Exception e) {
				System.out.println("Failed to notify group: " + e.getMessage());
			}
        } else if (bot != null && dispatchChatId == null && testDriverChatId != null) {
            try {
                BaseResponse r2 = bot.execute(new SendMessage(testDriverChatId, message));
                if (r2 == null || !r2.isOk()) {
                    System.out.println("Notify to test driver failed");
                } else {
                    System.out.println("Notify sent to test driver chat");
                }
            } catch (Exception ex) {}
        }
	}
	
	public void notifyGroupPickup(double lat, double lon) {
		if (bot != null && dispatchChatId != null) {
			try {
				String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
				
				// ENHANCED MAP BUTTON - BIGGER TEXT AND EMOJI
				InlineKeyboardButton mapBtn = new InlineKeyboardButton("🗺️ VIEW PICKUP LOCATION ON MAP").url(mapsUrl);
				
				// MAKE BUTTON STAND OUT MORE
				InlineKeyboardMarkup markup = new InlineKeyboardMarkup(new InlineKeyboardButton[]{mapBtn});
				
				// ENHANCED MESSAGE WITH CLEAR INSTRUCTIONS
				SendMessage msg = new SendMessage(dispatchChatId, "📍 PICKUP LOCATION:\nClick the button below to view on Google Maps");
				msg.replyMarkup(markup);
				
				BaseResponse r = bot.execute(msg);
				if (r == null) {
					System.out.println("Group pickup notify failed: null response");
                    if (testDriverChatId != null) {
                        try {
                            SendMessage fallback = new SendMessage(testDriverChatId, "[fallback] Pickup location:");
                            fallback.replyMarkup(markup);
                            bot.execute(fallback);
                            bot.execute(new SendLocation(testDriverChatId, (float) lat, (float) lon));
                            System.out.println("Fallback pickup notify sent to test driver chat");
                        } catch (Exception ex) {}
                    }
				} else if (!r.isOk()) {
					System.out.println("Group pickup notify failed: code=" + r.errorCode() + " desc=" + r.description());
                    if (testDriverChatId != null) {
                        try {
                            SendMessage fallback = new SendMessage(testDriverChatId, "[fallback] Pickup location:");
                            fallback.replyMarkup(markup);
                            bot.execute(fallback);
                            bot.execute(new SendLocation(testDriverChatId, (float) lat, (float) lon));
                            System.out.println("Fallback pickup notify sent to test driver chat");
                        } catch (Exception ex) {}
                    }
				}
				
				// ALSO SEND ACTUAL LOCATION PIN
				bot.execute(new SendLocation(dispatchChatId, (float) lat, (float) lon));
			} catch (Exception e) {
				System.out.println("Failed to send pickup map: " + e.getMessage());
			}
        } else if (bot != null && dispatchChatId == null && testDriverChatId != null) {
            try {
                String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
                
                // ENHANCED MAP BUTTON FOR FALLBACK
                InlineKeyboardButton mapBtn = new InlineKeyboardButton("🗺️ VIEW PICKUP LOCATION ON MAP").url(mapsUrl);
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup(new InlineKeyboardButton[]{mapBtn});
                
                SendMessage msg = new SendMessage(testDriverChatId, "📍 PICKUP LOCATION:\nClick the button below to view on Google Maps");
                msg.replyMarkup(markup);
                
                bot.execute(msg);
                bot.execute(new SendLocation(testDriverChatId, (float) lat, (float) lon));
                System.out.println("Pickup notify sent to test driver chat");
            } catch (Exception e) {}
        }
	}
	
	private String dialUrl(String p) {
		if (p == null) return null;
		String s = p.replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
		return "tel:" + s;
	}
	
    public void notifyGroupRequest(String message, String rideId, long passengerChatId, String phoneOpt) {
        if (bot == null) return;
        Long targetChatId = dispatchChatId != null ? dispatchChatId : testDriverChatId;
        if (targetChatId == null) return;
        try {
            SendMessage msg = new SendMessage(targetChatId, message);
            msg.parseMode(ParseMode.HTML);
            msg.disableWebPagePreview(true);
            
            // ENHANCED ACCEPT BUTTON - BIGGER, BOLDER, MORE VISIBLE
            InlineKeyboardButton acceptBtn = new InlineKeyboardButton("🟢 ✅ ACCEPT RIDE NOW").callbackData("ACCEPT:" + rideId + ":" + passengerChatId);
            
            // ENHANCED DECLINE BUTTON - BIGGER, BOLDER, MORE VISIBLE
            InlineKeyboardButton declineBtn = new InlineKeyboardButton("🔴 ❌ DECLINE RIDE").callbackData("DECLINE:" + rideId);
            
            // ARRANGE BUTTONS VERTICALLY FOR MAXIMUM VISIBILITY
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                new InlineKeyboardButton[][]{
                    {acceptBtn},
                    {declineBtn}
                }
            );
            
            msg.replyMarkup(markup);
            
            BaseResponse r = bot.execute(msg);
            if (r == null || !r.isOk()) {
                System.out.println("Group request notify failed: " + (r != null ? r.description() : "null"));
                if (dispatchChatId != null && testDriverChatId != null && !targetChatId.equals(testDriverChatId)) {
                    SendMessage fallback = new SendMessage(testDriverChatId, "[fallback]\n" + message);
                    fallback.parseMode(ParseMode.HTML);
                    fallback.disableWebPagePreview(true);
                    bot.execute(fallback);
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to send request with actions: " + e.getMessage());
        }
    }
	
	public void notifyGroupCall(String phone) {
		if (bot != null && dispatchChatId != null && phone != null && phone.length() > 0) {
			try {
				String url = dialUrl(phone);
				
				// ENHANCED CALL BUTTON - BIGGER AND MORE VISIBLE
				InlineKeyboardButton callBtn = new InlineKeyboardButton("📞 PHONE CLIENT NOW").url(url);
				
				InlineKeyboardMarkup markup = new InlineKeyboardMarkup(new InlineKeyboardButton[]{callBtn});
				
				// ENHANCED MESSAGE WITH CLEAR INSTRUCTIONS
				SendMessage msg = new SendMessage(dispatchChatId, "📱 CONTACT PASSENGER:\nClick below to call directly");
				msg.replyMarkup(markup);
				
				BaseResponse r = bot.execute(msg);
				if (r == null) {
					System.out.println("Group call notify failed: null response");
                    if (testDriverChatId != null) {
                        try {
                            SendMessage fallback = new SendMessage(testDriverChatId, "[fallback] Contact:");
                            fallback.replyMarkup(markup);
                            bot.execute(fallback);
                            System.out.println("Fallback call notify sent to test driver chat");
                        } catch (Exception ex) {}
                    }
				} else if (!r.isOk()) {
					System.out.println("Group call notify failed: code=" + r.errorCode() + " desc=" + r.description());
                    if (testDriverChatId != null) {
                        try {
                            SendMessage fallback = new SendMessage(testDriverChatId, "[fallback] Contact:");
                            fallback.replyMarkup(markup);
                            bot.execute(fallback);
                            System.out.println("Fallback call notify sent to test driver chat");
                        } catch (Exception ex) {}
                    }
				}
			} catch (Exception e) {
			}
        } else if (bot != null && dispatchChatId == null && phone != null && phone.length() > 0 && testDriverChatId != null) {
            try {
                String url = dialUrl(phone);
                
                // ENHANCED CALL BUTTON FOR FALLBACK
                InlineKeyboardButton callBtn = new InlineKeyboardButton("📞 PHONE CLIENT NOW").url(url);
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup(new InlineKeyboardButton[]{callBtn});
                
                SendMessage msg = new SendMessage(testDriverChatId, "📱 CONTACT PASSENGER:\nClick below to call directly");
                msg.replyMarkup(markup);
                
                bot.execute(msg);
            } catch (Exception e) {}
        }
	}
	
    private void handleAccept(Update update, String rideId, String passengerChatStr) {
        if (rideId == null || rideId.length() == 0) return;
        if (lockedRides.contains(rideId)) {
            return;
        }
        lockedRides.add(rideId);
        long driverUserId = update.callbackQuery().from() != null ? update.callbackQuery().from().id() : 0L;
        String driverName = update.callbackQuery().from() != null ? update.callbackQuery().from().firstName() : "Driver";
        ActivityLog.getInstance().addEvent(String.valueOf(driverUserId), "accept", rideId);
        try {
            if (this.myRide != null && rideId.equals(this.myRide.getRideId())) {
                this.myRide.setStatus("accepted");
                // Enrich ride with driver info if available
                AutoDriverInfo info = driverUserId > 0 ? autoRegisteredDrivers.get(driverUserId) : null;
                if (info != null) {
                    this.myRide.setDriverName(info.name != null && !info.name.isEmpty() ? info.name : driverName);
                    try { this.myRide.setVehiclePlate(info.vehiclePlate); } catch (Exception e) {}
                    try { this.myRide.setDriverPhone(info.phone); } catch (Exception e) {}
                } else {
                    this.myRide.setDriverName(driverName);
                }
            }
        } catch (Exception e) {}
        try {
            long passengerChatId = passengerChatStr != null && passengerChatStr.length() > 0 ? Long.parseLong(passengerChatStr) : 0L;
            if (passengerChatId > 0) {
                String notify = "✅ Your ride has been assigned\n\n" +
                    "Driver: " + (this.myRide != null && this.myRide.getDriverName() != null ? this.myRide.getDriverName() : driverName) + 
                    (this.myRide != null && this.myRide.getVehiclePlate() != null ? "\nPlate: " + this.myRide.getVehiclePlate() : "");
                this.sendMessage(passengerChatId, notify);
            }
        } catch (Exception e) {}
        try {
            String groupAssign = "✅ Ride assigned to " + (this.myRide != null && this.myRide.getDriverName() != null ? this.myRide.getDriverName() : driverName);
            this.notifyGroup(groupAssign);
        } catch (Exception e) {}
    }

    private void autoAssignDriverToRide(long passengerChatId) {
        try {
            if (this.myRide == null) return;
            java.util.List<Driver> active = DriverRegistry.getInstance().getActiveDriversWithLocations();
            Driver chosen = null;
            if (active != null && !active.isEmpty()) {
                chosen = active.get(0);
            } else {
                java.util.List<Driver> all = DriverRegistry.getInstance().getDrivers();
                if (all != null && !all.isEmpty()) chosen = all.get(0);
            }
            if (chosen != null) {
                this.myRide.setStatus("accepted");
                try { this.myRide.setDriverName(chosen.getName()); } catch (Exception e) {}
                try { this.myRide.setVehiclePlate(chosen.getVehiclePlate()); } catch (Exception e) {}
                try { this.myRide.setDriverPhone(chosen.getPhone()); } catch (Exception e) {}
                String notify = "✅ Your ride has been assigned\n\n" +
                    "Driver: " + (this.myRide.getDriverName() != null ? this.myRide.getDriverName() : "Driver") +
                    (this.myRide.getVehiclePlate() != null ? "\nPlate: " + this.myRide.getVehiclePlate() : "");
                if (passengerChatId > 0) this.sendMessage(passengerChatId, notify);
                this.notifyGroup("✅ Ride assigned to " + (this.myRide.getDriverName() != null ? this.myRide.getDriverName() : "Driver"));
            }
        } catch (Exception e) {}
    }
	
    /**
     * Process automatic driver registration from Telegram group events
     */
    private void processAutoDriverRegistration(Update update) {
        try {
            Message message = update.message();
            if (message == null) return;
            
            // Only process if this is the dispatch group
            if (dispatchChatId != null && message.chat().id() == dispatchChatId) {
                
                // Handle new chat members (auto-register as drivers)
                if (message.newChatMembers() != null) {
                    for (User user : message.newChatMembers()) {
                        if (!user.isBot()) {
                            autoRegisterDriver(user, dispatchChatId);
                        }
                    }
                }
                
                // Handle location updates from drivers
                if (message.location() != null && message.from() != null) {
                    updateDriverLocation(message.from().id(), message.location());
                }
                
                // Handle members leaving the group
                if (message.leftChatMember() != null) {
                    unregisterDriver(message.leftChatMember().id());
                }
            }
        } catch (Exception e) {
            System.err.println("Error in auto-driver registration: " + e.getMessage());
        }
    }
    
    /**
     * Auto-register a new driver from Telegram group
     */
    private void autoRegisterDriver(User user, Long groupId) {
        long userId = user.id();
        
        // Skip if already registered
        if (autoRegisteredDrivers.containsKey(userId)) {
            return;
        }
        
        // Create auto-driver info
        AutoDriverInfo driverInfo = new AutoDriverInfo();
        driverInfo.userId = userId;
        driverInfo.name = getFullName(user);
        driverInfo.phone = "";
        driverInfo.email = user.username() != null ? user.username() + "@telegram.com" : "";
        driverInfo.vehicleType = "Auto-Registered";
        driverInfo.vehiclePlate = "AUTO-" + userId;
        driverInfo.status = "active";
        driverInfo.isOnline = true;
        driverInfo.joinDate = System.currentTimeMillis();
        driverInfo.lastActivity = System.currentTimeMillis();
        
        // Add to auto-registered drivers
        autoRegisteredDrivers.put(userId, driverInfo);
        
        // Register in main driver registry
        Driver driver = new Driver(userId, driverInfo.name, driverInfo.phone, driverInfo.email);
        driver.setVehicleType(driverInfo.vehicleType);
        driver.setVehiclePlate(driverInfo.vehiclePlate);
        driver.setStatus(driverInfo.status);
        driver.setOnline(driverInfo.isOnline);
        
        DriverRegistry.getInstance().addDriver(driver);
        
        // Log the registration
        ActivityLog.getInstance().addEvent(
            String.valueOf(groupId), 
            "DRIVER_AUTO_REGISTERED", 
            "Auto-registered driver: " + driverInfo.name + " (Telegram ID: " + userId + ")"
        );
        
        // Send welcome message
        String welcomeMessage = String.format(
            "🚗 Welcome %s! You've been automatically registered as a driver (ID: %d). " +
            "Share your location to appear on the dashboard map.", 
            driverInfo.name, userId
        );
        
        try {
            bot.execute(new SendMessage(groupId, welcomeMessage));
        } catch (Exception e) {
            System.err.println("Failed to send welcome message: " + e.getMessage());
        }
        
        System.out.println("Auto-registered driver: " + driverInfo.name + " (ID: " + userId + ")");
    }
    
    /**
     * Update driver location from Telegram location share
     */
    private void updateDriverLocation(long userId, com.pengrad.telegrambot.model.Location location) {
        AutoDriverInfo driverInfo = autoRegisteredDrivers.get(userId);
        if (driverInfo != null) {
            driverInfo.lastLocation = new GeoLocation(location.latitude(), location.longitude());
            driverInfo.lastLocationUpdate = System.currentTimeMillis();
            driverInfo.lastActivity = System.currentTimeMillis();
            driverInfo.isOnline = true;
            
            // Update in main driver registry
            DriverRegistry.getInstance().updateDriverLocation(
                userId, 
                (float)location.latitude(), 
                (float)location.longitude(), 
                0 // bearing
            );
            
            DriverRegistry.getInstance().updateDriverStatus(userId, "active");
            DriverRegistry.getInstance().updateDriverOnlineStatus(userId, true);
            
            // Log location update
            ActivityLog.getInstance().addEvent(
                String.valueOf(dispatchChatId),
                "DRIVER_LOCATION_UPDATE",
                String.format("Driver %s location updated: %.6f, %.6f", 
                    driverInfo.name, location.latitude(), location.longitude())
            );
            
            System.out.println("Updated location for driver: " + driverInfo.name);
        }
    }
    
    /**
     * Unregister driver who left the group
     */
    private void unregisterDriver(long userId) {
        AutoDriverInfo driverInfo = autoRegisteredDrivers.remove(userId);
        if (driverInfo != null) {
            DriverRegistry.getInstance().updateDriverStatus(userId, "inactive");
            DriverRegistry.getInstance().updateDriverOnlineStatus(userId, false);
            
            ActivityLog.getInstance().addEvent(
                String.valueOf(dispatchChatId),
                "DRIVER_UNREGISTERED",
                "Driver unregistered (left group): " + driverInfo.name
            );
            
            System.out.println("Unregistered driver: " + driverInfo.name);
        }
    }
    
    /**
     * Get full name from Telegram user
     */
    private String getFullName(User user) {
        StringBuilder name = new StringBuilder();
        if (user.firstName() != null) {
            name.append(user.firstName());
        }
        if (user.lastName() != null) {
            if (name.length() > 0) name.append(" ");
            name.append(user.lastName());
        }
        return name.length() > 0 ? name.toString() : "Unknown Driver";
    }
    
    /**
     * Get all auto-registered drivers
     */
    public java.util.List<AutoDriverInfo> getAutoRegisteredDrivers() {
        return new java.util.ArrayList<>(autoRegisteredDrivers.values());
    }
    
    /**
     * Inner class for auto-registered driver information
     */
    public static class AutoDriverInfo {
        public long userId;
        public String name;
        public String phone;
        public String email;
        public String vehicleType;
        public String vehiclePlate;
        public String status;
        public boolean isOnline;
        public long joinDate;
        public long lastActivity;
        public GeoLocation lastLocation;
        public long lastLocationUpdate;
        
        @Override
        public String toString() {
            return String.format("AutoDriverInfo{name='%s', userId=%d, status='%s', online=%s}", 
                name, userId, status, isOnline);
        }
    }
}
