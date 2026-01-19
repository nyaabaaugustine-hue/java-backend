import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActivityLog {
    private static final ActivityLog INSTANCE = new ActivityLog();
    private final List<ActivityEntry> events = new ArrayList<>();
    private final Map<Long, TransportRide> ridesByChat = new LinkedHashMap<>();
    private final List<AuditEntry> auditTrail = new ArrayList<>(); // Security audit trail
    
    public static ActivityLog getInstance() { return INSTANCE; }
    
    public synchronized void addEvent(String actor, String action, String detail) {
        ActivityEntry entry = new ActivityEntry(System.currentTimeMillis(), actor, action, detail);
        events.add(entry);
        if (events.size() > 1000) events.remove(0);
        
        // Add to security audit trail
        auditTrail.add(new AuditEntry(
            System.currentTimeMillis(),
            actor,
            action,
            detail,
            getIpAddress(), // Would need to be passed from request context
            getUserAgent()  // Would need to be passed from request context
        ));
        if (auditTrail.size() > 5000) auditTrail.remove(0);
    }
    
    public synchronized List<ActivityEntry> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
    
    public synchronized List<AuditEntry> getAuditTrail() {
        return Collections.unmodifiableList(new ArrayList<>(auditTrail));
    }
    
    public synchronized void setCurrentRide(long chatId, TransportRide ride) {
        ridesByChat.put(chatId, ride);
    }
    
    public synchronized Map<Long, TransportRide> getRidesByChat() {
        return new LinkedHashMap<>(ridesByChat);
    }
    
    // Helper methods for audit logging
    private String getIpAddress() {
        // This would need to be implemented to get actual IP from request
        return "127.0.0.1";
    }
    
    private String getUserAgent() {
        // This would need to be implemented to get actual user agent
        return "Telegram-Bot";
    }
}

// New Audit Entry class for enhanced security logging
class AuditEntry {
    private final long timestamp;
    private final String actor;
    private final String action;
    private final String detail;
    private final String ipAddress;
    private final String userAgent;
    private final String sessionId;
    
    public AuditEntry(long timestamp, String actor, String action, String detail, String ipAddress, String userAgent) {
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.sessionId = java.util.UUID.randomUUID().toString();
    }
    
    // Getters
    public long getTimestamp() { return timestamp; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getSessionId() { return sessionId; }
}
