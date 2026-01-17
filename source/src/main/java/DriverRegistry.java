import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DriverRegistry {
    private static final DriverRegistry INSTANCE = new DriverRegistry();
    private final List<Driver> drivers = new ArrayList<>();
    
    public static DriverRegistry getInstance() { 
        return INSTANCE; 
    }
    
    public synchronized void addDriver(Driver driver) {
        drivers.add(driver);
    }
    
    public synchronized List<Driver> getDrivers() {
        return Collections.unmodifiableList(new ArrayList<>(drivers));
    }
    
    public synchronized Driver getDriverById(long id) {
        return drivers.stream()
            .filter(d -> d.getId() == id)
            .findFirst()
            .orElse(null);
    }
    
    public synchronized void updateDriverStatus(long id, String status) {
        Driver driver = getDriverById(id);
        if (driver != null) {
            driver.setStatus(status);
        }
    }
    
    public synchronized void updateDriverOnlineStatus(long id, boolean isOnline) {
        Driver driver = getDriverById(id);
        if (driver != null) {
            driver.setOnline(isOnline);
        }
    }
}

class Driver {
    private long id;
    private String name;
    private String phone;
    private String email;
    private String vehicleType;
    private String vehiclePlate;
    private String vehicleModel;
    private String vehicleYear;
    private String vehicleColor;
    private int capacity;
    private String licenseNumber;
    private String licenseExpiry;
    private boolean isOnline;
    private String status; // pending, active, suspended, inactive
    private double rating;
    private int totalTrips;
    private double earnings;
    
    public Driver(long id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.status = "pending";
        this.isOnline = false;
        this.rating = 0.0;
        this.totalTrips = 0;
        this.earnings = 0.0;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }
    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }
    public String getVehicleYear() { return vehicleYear; }
    public void setVehicleYear(String vehicleYear) { this.vehicleYear = vehicleYear; }
    public String getVehicleColor() { return vehicleColor; }
    public void setVehicleColor(String vehicleColor) { this.vehicleColor = vehicleColor; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(String licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getTotalTrips() { return totalTrips; }
    public void setTotalTrips(int totalTrips) { this.totalTrips = totalTrips; }
    public void incrementTrips() { this.totalTrips++; }
    public double getEarnings() { return earnings; }
    public void setEarnings(double earnings) { this.earnings = earnings; }
    public void addEarnings(double amount) { this.earnings += amount; }
}
