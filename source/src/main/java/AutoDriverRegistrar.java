import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Automatic Driver Registration System
 * Monitors Telegram driver group and auto-registers members as drivers
 */
public class AutoDriverRegistrar {
    private TelegramBot bot;
    private Long driverGroupId;
    private Map<Long, DriverInfo> registeredDrivers;
    private DriverRegistry driverRegistry;
    
    public AutoDriverRegistrar(TelegramBot bot, Long driverGroupId) {
        this.bot = bot;
        this.driverGroupId = driverGroupId;
        this.registeredDrivers = new ConcurrentHashMap<>();
        this.driverRegistry = DriverRegistry.getInstance();
    }
    
    /**
     * Process incoming updates to detect new members and location shares
     */
    public void processUpdate(Update update) {
        try {
            // Handle new chat members
            if (update.message() != null && update.message().newChatMembers() != null) {
                handleNewMembers(update.message());
            }
            
            // Handle location updates from group members
            if (update.message() != null && update.message().location() != null) {
                handleLocationUpdate(update.message());
            }
            
            // Handle left members
            if (update.message() != null && update.message().leftChatMember() != null) {
                handleMemberLeft(update.message());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing auto-registration: " + e.getMessage());
        }
    }
    
    /**
     * Handle new members joining the driver group
     */
    private void handleNewMembers(Message message) {
        if (message.chat().id() != driverGroupId) return;
        
        User[] newMembers = message.newChatMembers();
        for (User user : newMembers) {
            if (!user.isBot()) {
                registerNewDriver(user, message.chat().id());
            }
        }
    }
    
    /**
     * Handle location updates from group members
     */
    private void handleLocationUpdate(Message message) {
        if (message.chat().id() != driverGroupId) return;
        
        User sender = message.from();
        if (sender == null) return;
        
        long userId = sender.id();
        
        // Check if user is already registered as driver
        if (registeredDrivers.containsKey(userId)) {
            DriverInfo driverInfo = registeredDrivers.get(userId);
            updateDriverLocation(driverInfo, message.location());
            
            // Send confirmation to group
            String confirmation = String.format(
                "Driver %s updated location: %.6f, %.6f", 
                driverInfo.name, 
                message.location().latitude(), 
                message.location().longitude()
            );
            
            try {
                bot.execute(new SendMessage(driverGroupId, confirmation));
            } catch (Exception e) {
                System.err.println("Failed to send location confirmation: " + e.getMessage());
            }
        } else {
            // Auto-register the driver if not already registered
            registerNewDriver(sender, driverGroupId);
            DriverInfo driverInfo = registeredDrivers.get(userId);
            if (driverInfo != null) {
                updateDriverLocation(driverInfo, message.location());
            }
        }
    }
    
    /**
     * Handle members leaving the group
     */
    private void handleMemberLeft(Message message) {
        if (message.chat().id() != driverGroupId) return;
        
        User leftUser = message.leftChatMember();
        if (leftUser != null && registeredDrivers.containsKey(leftUser.id())) {
            unregisterDriver(leftUser.id());
        }
    }
    
    /**
     * Register a new driver from Telegram user
     */
    private void registerNewDriver(User user, Long groupId) {
        long userId = user.id();
        
        // Skip if already registered
        if (registeredDrivers.containsKey(userId)) {
            return;
        }
        
        // Create driver info from Telegram user data
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.userId = userId;
        driverInfo.telegramId = userId;
        driverInfo.name = getFullName(user);
        driverInfo.phone = ""; // Will be updated when user shares contact
        driverInfo.email = user.username() != null ? user.username() + "@telegram.com" : "";
        driverInfo.vehicleType = "Unknown";
        driverInfo.vehiclePlate = "TEMP-" + userId;
        driverInfo.status = "active";
        driverInfo.isOnline = true;
        driverInfo.joinDate = System.currentTimeMillis();
        driverInfo.lastActivity = System.currentTimeMillis();
        
        // Add to registered drivers map
        registeredDrivers.put(userId, driverInfo);
        
        // Register in main driver registry
        Driver driver = new Driver(userId, driverInfo.name, driverInfo.phone, driverInfo.email);
        driver.setVehicleType(driverInfo.vehicleType);
        driver.setVehiclePlate(driverInfo.vehiclePlate);
        driver.setStatus(driverInfo.status);
        driver.setOnline(driverInfo.isOnline);
        
        driverRegistry.addDriver(driver);
        
        // Log the registration
        ActivityLog.getInstance().addEvent(
            String.valueOf(groupId), 
            "DRIVER_AUTO_REGISTERED", 
            "Auto-registered driver: " + driverInfo.name + " (Telegram ID: " + userId + ")"
        );
        
        // Send welcome message to group
        String welcomeMessage = String.format(
            "🚗 Welcome %s! You've been automatically registered as a driver. " +
            "Share your location to appear on the dashboard map. " +
            "Your driver ID: %d", 
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
     * Update driver location
     */
    private void updateDriverLocation(DriverInfo driverInfo, com.pengrad.telegrambot.model.Location location) {
        driverInfo.lastLocation = new GeoLocation(location.latitude(), location.longitude());
        driverInfo.lastLocationUpdate = System.currentTimeMillis();
        driverInfo.lastActivity = System.currentTimeMillis();
        driverInfo.isOnline = true;
        
        // Update in main driver registry
        driverRegistry.updateDriverLocation(
            driverInfo.userId, 
            (float)location.latitude(), 
            (float)location.longitude(), 
            0 // bearing
        );
        
        // Update driver status to active
        driverRegistry.updateDriverStatus(driverInfo.userId, "active");
        driverRegistry.updateDriverOnlineStatus(driverInfo.userId, true);
        
        // Log location update
        ActivityLog.getInstance().addEvent(
            String.valueOf(driverGroupId),
            "DRIVER_LOCATION_UPDATE",
            String.format("Driver %s location updated: %.6f, %.6f", 
                driverInfo.name, location.latitude(), location.longitude())
        );
    }
    
    /**
     * Unregister a driver who left the group
     */
    private void unregisterDriver(long userId) {
        DriverInfo driverInfo = registeredDrivers.remove(userId);
        if (driverInfo != null) {
            // Mark driver as inactive in registry
            driverRegistry.updateDriverStatus(userId, "inactive");
            driverRegistry.updateDriverOnlineStatus(userId, false);
            
            ActivityLog.getInstance().addEvent(
                String.valueOf(driverGroupId),
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
     * Get list of all registered drivers
     */
    public List<DriverInfo> getRegisteredDrivers() {
        return new ArrayList<>(registeredDrivers.values());
    }
    
    /**
     * Get driver info by Telegram user ID
     */
    public DriverInfo getDriverInfo(long userId) {
        return registeredDrivers.get(userId);
    }
    
    /**
     * Manual driver registration (for testing)
     */
    public void manualRegisterDriver(long userId, String name, String phone) {
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.userId = userId;
        driverInfo.telegramId = userId;
        driverInfo.name = name;
        driverInfo.phone = phone;
        driverInfo.email = "";
        driverInfo.vehicleType = "Manual Registration";
        driverInfo.vehiclePlate = "MANUAL-" + userId;
        driverInfo.status = "active";
        driverInfo.isOnline = true;
        driverInfo.joinDate = System.currentTimeMillis();
        driverInfo.lastActivity = System.currentTimeMillis();
        
        registeredDrivers.put(userId, driverInfo);
        
        Driver driver = new Driver(userId, name, phone, "");
        driver.setVehicleType(driverInfo.vehicleType);
        driver.setVehiclePlate(driverInfo.vehiclePlate);
        driver.setStatus(driverInfo.status);
        driver.setOnline(driverInfo.isOnline);
        
        driverRegistry.addDriver(driver);
        
        System.out.println("Manually registered driver: " + name);
    }
    
    /**
     * Inner class to store driver information
     */
    public static class DriverInfo {
        public long userId;
        public long telegramId;
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
            return String.format("DriverInfo{name='%s', userId=%d, status='%s', online=%s}", 
                name, userId, status, isOnline);
        }
    }
}