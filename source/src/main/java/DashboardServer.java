import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DashboardServer {
    private HttpServer server;
    private final int port;
    public DashboardServer(int port) { this.port = port; }
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/dashboard", new DashboardHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/rides", new RidesHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Dashboard running on http://localhost:" + port + "/dashboard");
    }
    static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><title>CyberCando Dashboard</title>");
            sb.append("<style>body{font-family:Arial}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:8px}th{background:#f4f4f4}</style>");
            sb.append("</head><body>");
            sb.append("<h2>CyberCando Transport Dashboard</h2>");
            Map<Long, TransportRide> rides = ActivityLog.getInstance().getRidesByChat();
            sb.append("<h3>Active Rides</h3><table><tr><th>Chat</th><th>Product</th><th>Status</th><th>Driver</th></tr>");
            for (Map.Entry<Long, TransportRide> e : rides.entrySet()) {
                TransportRide r = e.getValue();
                sb.append("<tr>");
                sb.append("<td>").append(e.getKey()).append("</td>");
                sb.append("<td>").append(r != null && r.getProduct()!=null ? r.getProduct().getDisplayName() : "").append("</td>");
                sb.append("<td>").append(r != null ? r.getStatus() : "").append("</td>");
                sb.append("<td>").append(r != null ? (r.getDriverName()!=null ? r.getDriverName() : "") : "").append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
            sb.append("<h3>Recent Events</h3><table><tr><th>Time</th><th>Actor</th><th>Action</th><th>Detail</th></tr>");
            List<ActivityEntry> events = ActivityLog.getInstance().getEvents();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (int i = events.size()-1; i >= 0 && i >= events.size()-100; i--) {
                ActivityEntry ev = events.get(i);
                sb.append("<tr>");
                sb.append("<td>").append(fmt.format(new Date(ev.getTimestamp()))).append("</td>");
                sb.append("<td>").append(ev.getActor()).append("</td>");
                sb.append("<td>").append(ev.getAction()).append("</td>");
                sb.append("<td>").append(ev.getDetail()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
            sb.append("<p>API: <a href=\"/api/events\">/api/events</a> | <a href=\"/api/rides\">/api/rides</a></p>");
            sb.append("</body></html>");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
    static class EventsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            List<ActivityEntry> events = ActivityLog.getInstance().getEvents();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < events.size(); i++) {
                ActivityEntry ev = events.get(i);
                sb.append("{\"timestamp\":").append(ev.getTimestamp())
                  .append(",\"actor\":\"").append(escape(ev.getActor())).append("\"")
                  .append(",\"action\":\"").append(escape(ev.getAction())).append("\"")
                  .append(",\"detail\":\"").append(escape(ev.getDetail())).append("\"}");
                if (i < events.size()-1) sb.append(",");
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
        }
    }
    static class RidesHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Map<Long, TransportRide> rides = ActivityLog.getInstance().getRidesByChat();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int idx = 0;
            for (Map.Entry<Long, TransportRide> e : rides.entrySet()) {
                TransportRide r = e.getValue();
                sb.append("{\"chat\":").append(e.getKey())
                  .append(",\"product\":\"").append(r != null && r.getProduct()!=null ? r.getProduct().getDisplayName() : "").append("\"")
                  .append(",\"status\":\"").append(r != null ? r.getStatus() : "").append("\"")
                  .append(",\"driver\":\"").append(r != null && r.getDriverName()!=null ? r.getDriverName() : "").append("\"")
                  .append("}");
                if (idx < rides.size()-1) sb.append(",");
                idx++;
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
}
