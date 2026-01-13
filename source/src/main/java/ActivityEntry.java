public class ActivityEntry {
    private final long timestamp;
    private final String actor;
    private final String action;
    private final String detail;
    public ActivityEntry(long timestamp, String actor, String action, String detail) {
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
    }
    public long getTimestamp() { return timestamp; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
