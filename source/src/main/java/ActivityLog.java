import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActivityLog {
    private static final ActivityLog INSTANCE = new ActivityLog();
    private final List<ActivityEntry> events = new ArrayList<>();
    private final Map<Long, TransportRide> ridesByChat = new LinkedHashMap<>();
    public static ActivityLog getInstance() { return INSTANCE; }
    public synchronized void addEvent(String actor, String action, String detail) {
        events.add(new ActivityEntry(System.currentTimeMillis(), actor, action, detail));
        if (events.size() > 1000) events.remove(0);
    }
    public synchronized List<ActivityEntry> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
    public synchronized void setCurrentRide(long chatId, TransportRide ride) {
        ridesByChat.put(chatId, ride);
    }
    public synchronized Map<Long, TransportRide> getRidesByChat() {
        return new LinkedHashMap<>(ridesByChat);
    }
}
