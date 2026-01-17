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
        server.createContext("/api/drivers", new DriversHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Dashboard running on http://localhost:" + port + "/dashboard");
    }
    static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            sb.append("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
            sb.append("<title>CyberCando Dashboard</title></head><body class=\"bg-light\">");
            sb.append("<div class=\"container py-4\">");
            sb.append("<div class=\"d-flex justify-content-between align-items-center mb-3\"><h2 class=\"mb-0\">CyberCando Transport Dashboard</h2><button id=\"refresh\" class=\"btn btn-primary\">Refresh</button></div>");
            sb.append("<div class=\"row\">");
            sb.append("<div class=\"col-md-6\"><div class=\"card mb-4\"><div class=\"card-header\">Active Rides</div><div class=\"card-body\">");
            sb.append("<table class=\"table table-sm\" id=\"rides\"><thead><tr><th>Chat</th><th>Product</th><th>Status</th><th>Driver</th></tr></thead><tbody></tbody></table>");
            sb.append("</div></div></div>");
            sb.append("<div class=\"col-md-6\"><div class=\"card mb-4\"><div class=\"card-header\">Recent Events</div><div class=\"card-body\">");
            sb.append("<table class=\"table table-sm\" id=\"events\"><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Detail</th></tr></thead><tbody></tbody></table>");
            sb.append("</div></div></div>");
            sb.append("</div>");
            sb.append("<div class=\"text-muted\">API: <a href=\"/api/events\">/api/events</a> | <a href=\"/api/rides\">/api/rides</a></div>");
            sb.append("</div>");
            sb.append("<script>");
            sb.append("function fmtTime(ts){var d=new Date(ts);return d.toLocaleString();}");
            sb.append("async function load(){try{var r=await fetch('/api/rides');var rides=await r.json();var e=await fetch('/api/events');var events=await e.json();");
            sb.append("var rt=document.querySelector('#rides tbody');rt.innerHTML='';for(var i=0;i<rides.length;i++){var x=rides[i];rt.insertAdjacentHTML('beforeend','<tr><td>'+x.chat+'</td><td>'+(x.product||'')+'</td><td>'+(x.status||'')+'</td><td>'+(x.driver||'')+'</td></tr>');}");
            sb.append("var et=document.querySelector('#events tbody');et.innerHTML='';for(var j=events.length-1;j>=Math.max(0,events.length-100);j--){var ev=events[j];et.insertAdjacentHTML('beforeend','<tr><td>'+fmtTime(ev.timestamp)+'</td><td>'+ev.actor+'</td><td>'+ev.action+'</td><td>'+ev.detail+'</td></tr>');}");
            sb.append("}catch(err){}}");
            sb.append("document.addEventListener('DOMContentLoaded',function(){document.getElementById('refresh').addEventListener('click',load);load();setInterval(load,5000);});");
            sb.append("</script>");
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
                  .append(",\"detail\":\"").append(escape(ev.getDetail())).append("\"}")
                if (i < events.size()-1) sb.append(",");
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
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
                  .append(",\"fare\":\"").append(r != null && r.getFareDisplay()!=null ? r.getFareDisplay() : "").append("\"")
                  .append(",\"phone\":\"").append(r != null && r.getPassengerPhone()!=null ? r.getPassengerPhone() : "").append("\"")
                  .append("}");
                if (idx < rides.size()-1) sb.append(",");
                idx++;
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
    static class DriversHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                handleGetDrivers(exchange);
            } else if ("POST".equals(method)) {
                handleCreateDriver(exchange);
            } else if ("PUT".equals(method)) {
                handleUpdateDriver(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
        private void handleGetDrivers(HttpExchange exchange) throws IOException {
            List<Driver> drivers = DriverRegistry.getInstance().getDrivers();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < drivers.size(); i++) {
                Driver d = drivers.get(i);
                sb.append("{\"id\":").append(d.getId())
                  .append(",\"name\":\"").append(escape(d.getName())).append("\"")
                  .append(",\"phone\":\"").append(escape(d.getPhone())).append("\"")
                  .append(",\"email\":\"").append(escape(d.getEmail())).append("\"")
                  .append(",\"vehicleType\":\"").append(escape(d.getVehicleType())).append("\"")
                  .append(",\"vehiclePlate\":\"").append(escape(d.getVehiclePlate())).append("\"")
                  .append(",\"vehicleModel\":\"").append(escape(d.getVehicleModel())).append("\"")
                  .append(",\"vehicleYear\":\"").append(escape(d.getVehicleYear())).append("\"")
                  .append(",\"vehicleColor\":\"").append(escape(d.getVehicleColor())).append("\"")
                  .append(",\"capacity\":").append(d.getCapacity())
                  .append(",\"licenseNumber\":\"").append(escape(d.getLicenseNumber())).append("\"")
                  .append(",\"licenseExpiry\":\"").append(escape(d.getLicenseExpiry())).append("\"")
                  .append(",\"isOnline\":").append(d.isOnline())
                  .append(",\"status\":\"").append(escape(d.getStatus())).append("\"")
                  .append(",\"rating\":").append(d.getRating())
                  .append(",\"totalTrips\":").append(d.getTotalTrips())
                  .append(",\"earnings\":").append(d.getEarnings())
                  .append("}");
                if (i < drivers.size()-1) sb.append(",");
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
        private void handleCreateDriver(HttpExchange exchange) throws IOException {
            // Read request body
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(exchange.getRequestBody(), "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            // Simple JSON parsing (basic implementation)
            String json = body.toString();
            long id = System.currentTimeMillis();
            String name = extractValue(json, "name");
            String phone = extractValue(json, "phone");
            String email = extractValue(json, "email");
            
            Driver driver = new Driver(id, name, phone, email);
            driver.setVehicleType(extractValue(json, "vehicleType"));
            driver.setVehiclePlate(extractValue(json, "vehiclePlate"));
            driver.setVehicleModel(extractValue(json, "vehicleModel"));
            driver.setVehicleYear(extractValue(json, "vehicleYear"));
            driver.setVehicleColor(extractValue(json, "vehicleColor"));
            driver.setCapacity(extractInt(json, "capacity", 4));
            driver.setLicenseNumber(extractValue(json, "licenseNumber"));
            driver.setLicenseExpiry(extractValue(json, "licenseExpiry"));
            
            DriverRegistry.getInstance().addDriver(driver);
            String vehiclePlate = driver.getVehiclePlate();
            ActivityLog.getInstance().addEvent("System", "Driver Onboarded", name + " - " + vehiclePlate);
            
            String response = "{\"success\":true,\"id\":" + id + "}";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
        private void handleUpdateDriver(HttpExchange exchange) throws IOException {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(exchange.getRequestBody(), "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            String json = body.toString();
            long id = extractLong(json, "id", 0);
            String status = extractValue(json, "status");
            boolean isOnline = extractBoolean(json, "isOnline");
            
            if (status != null && !status.isEmpty()) {
                DriverRegistry.getInstance().updateDriverStatus(id, status);
            }
            DriverRegistry.getInstance().updateDriverOnlineStatus(id, isOnline);
            
            String response = "{\"success\":true}";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
        private String extractValue(String json, String key) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        }
        private int extractInt(String json, String key, int defaultValue) {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern);
            if (start == -1) return defaultValue;
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return defaultValue;
            try {
                return Integer.parseInt(json.substring(start, end).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        private long extractLong(String json, String key, long defaultValue) {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern);
            if (start == -1) return defaultValue;
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return defaultValue;
            try {
                return Long.parseLong(json.substring(start, end).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        private boolean extractBoolean(String json, String key) {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern);
            if (start == -1) return false;
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return false;
            return "true".equals(json.substring(start, end).trim());
        }
        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
        }
    }
}
