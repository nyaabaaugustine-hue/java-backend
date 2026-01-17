
public class TransportRide {
    private String rideId;
    private String status; // processing, accepted, arriving, in_progress, completed, canceled
    private TransportProduct product;
    private String vehicleViewId;
    private String driverName;
    private String driverPhone;
    private String vehiclePlate;
    private String vehicleModel;
    private String fareDisplay;
    private String passengerPhone;

    public TransportRide(String rideId, String status, TransportProduct product) {
        this.rideId = rideId;
        this.status = status;
        this.product = product;
    }

    public String getRideId() { return rideId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public TransportProduct getProduct() { return product; }
    
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    
    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }
    
    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }
    
    public String getFareDisplay() { return fareDisplay; }
    public void setFareDisplay(String fareDisplay) { this.fareDisplay = fareDisplay; }
    
    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }
}
