
public class TransportFare {
    private String fareId;
    private String value;
    private String currencyCode;
    private String display;

    public TransportFare(String fareId, String value, String currencyCode, String display) {
        this.fareId = fareId;
        this.value = value;
        this.currencyCode = currencyCode;
        this.display = display;
    }

    public String getFareId() { return fareId; }
    public String getValue() { return value; }
    public String getCurrencyCode() { return currencyCode; }
    public String getDisplay() { return display; }
}
