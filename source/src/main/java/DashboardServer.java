import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread serverThread;
    private final int port;
    private AutoDriverRegistrar autoRegistrar;
    private ScheduledExecutorService scheduler;
    private Map<String, Object> dashboardState;
    
    public DashboardServer(int port) {
        this(port, null);
    }
    
    public DashboardServer(int port, AutoDriverRegistrar autoRegistrar) {
        this.port = port;
        this.autoRegistrar = autoRegistrar; // Can be null if Telegram integration not needed
        this.dashboardState = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        
        serverThread = new Thread(this::handleRequests);
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Start periodic updates
        startPeriodicUpdates();
        
        System.out.println("Enhanced Dashboard Server running on http://localhost:" + port);
    }
    
    private void handleRequests() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleRequest(clientSocket)).start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleRequest(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            
            // Read the request line
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            
            String method = parts[0];
            String path = parts[1];
            
            // Read headers
            String headerLine;
            Map<String, String> headers = new HashMap<>();
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String headerName = headerLine.substring(0, colonIndex).trim();
                    String headerValue = headerLine.substring(colonIndex + 1).trim();
                    headers.put(headerName.toLowerCase(), headerValue);
                }
            }
            
            // Read body if present
            String requestBody = null;
            if ("POST".equalsIgnoreCase(method)) {
                int contentLength = 0;
                String contentLengthHeader = headers.get("content-length");
                if (contentLengthHeader != null) {
                    try {
                        contentLength = Integer.parseInt(contentLengthHeader);
                        char[] bodyChars = new char[contentLength];
                        int totalRead = 0;
                        while (totalRead < contentLength) {
                            int read = in.read(bodyChars, totalRead, contentLength - totalRead);
                            if (read == -1) break;
                            totalRead += read;
                        }
                        requestBody = new String(bodyChars, 0, totalRead);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid content length: " + contentLengthHeader);
                    }
                }
            }
            
            // Route based on path
            String response = routeRequest(method, path, requestBody);
            
            // Send response
            out.print(response);
            out.flush();
            
        } catch (IOException e) {
            System.err.println("Error handling request: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
    
    private String routeRequest(String method, String path, String requestBody) {
        try {
            if (path.equals("/") || path.startsWith("/index.html")) {
                return handleRoot(requestBody);
            } else if (path.equals("/dashboard")) {
                return handleDashboard(requestBody);
            } else if (path.equals("/api/rides")) {
                return handleRides(requestBody);
            } else if (path.equals("/api/drivers")) {
                return handleDrivers(requestBody);
            } else if (path.equals("/api/drivers/location")) {
                if ("POST".equalsIgnoreCase(method)) {
                    return handleUpdateDriverLocation(requestBody);
                } else {
                    return handleDriverLocation(requestBody);
                }
            } else if (path.equals("/api/events")) {
                return handleEvents(requestBody);
            } else if (path.equals("/api/audit")) {
                return handleAuditTrail(requestBody);
            } else if (path.equals("/api/live/drivers")) {
                return handleLiveDrivers(requestBody);
            } else if (path.equals("/api/driver/register")) {
                return handleDriverRegistration(method, requestBody);
            } else {
                return createResponse(404, "Not Found", "text/plain", "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing request for " + path + ": " + e.getMessage());
            return createResponse(500, "Internal Server Error", "application/json", 
                "{\"error\":\"Internal server error\"}");
        }
    }
    
    private String handleRoot(String requestBody) {
        return handleDashboard(requestBody);
    }
    
    private String handleDashboard(String requestBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        sb.append("<title>Cyber Move Dashboard</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" integrity=\"sha256-p4Nx9F1ZUHfM2zNwFfMrM3O8V8LQ6WZk5QmTTVfWgolM=\" crossorigin=\"\"/>");
        sb.append("<style>");
        sb.append("body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;margin:0;padding:0;background:#0f172a;color:#e2e8f0}");
        sb.append(".container{padding:16px;max-width:1200px;margin:0 auto}");
        sb.append("h1{font-size:22px;margin:0 0 12px}");
        sb.append("#map{height:60vh;border:1px solid #334155;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.2);overflow:hidden}");
        sb.append(".toolbar{display:flex;gap:8px;align-items:center;margin:12px 0}");
        sb.append(".badge{display:inline-block;padding:6px 10px;border-radius:9999px;background:#1e293b;color:#93c5fd;font-weight:600;font-size:12px}");
        sb.append(".list{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:8px;margin-top:12px}");
        sb.append(".driver{background:#0b1220;border:1px solid #1e293b;border-radius:8px;padding:10px}");
        sb.append("</style></head><body>");
        sb.append("<div class=\"container\">");
        sb.append("<h1>Cyber Move – Live Drivers</h1>");
        sb.append("<div class=\"toolbar\"><span id=\"stats\" class=\"badge\">Loading…</span></div>");
        sb.append("<div id=\"map\"></div>");
        sb.append("<div id=\"list\" class=\"list\"></div>");
        sb.append("</div>");
        sb.append("<script crossorigin src=\"https://unpkg.com/react@18/umd/react.production.min.js\"></script>");
        sb.append("<script crossorigin src=\"https://unpkg.com/react-dom@18/umd/react-dom.production.min.js\"></script>");
        sb.append("<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\" integrity=\"sha256-20nQCchB9co0qIjJZRGuk2/Z9VM8yy2jVQvM+Z8M2dk=\" crossorigin=\"\"></script>");
        sb.append("<script>");
        sb.append("(function(){");
        sb.append("var e=React.createElement;");
        sb.append("function App(){");
        sb.append("var _React=React,useState=_React.useState,useEffect=_React.useEffect;");
        sb.append("var _a=useState([]),drivers=_a[0],setDrivers=_a[1];");
        sb.append("useEffect(function(){");
        sb.append("var load=function(){fetch('/api/live/drivers').then(function(r){return r.json()}).then(function(j){setDrivers(j.drivers||[])})['catch'](function(){})};");
        sb.append("load();var id=setInterval(load,5000);return function(){clearInterval(id)};");
        sb.append("},[]);");
        sb.append("useEffect(function(){");
        sb.append("var stats=document.getElementById('stats');if(stats){stats.textContent='Active drivers: '+drivers.length}");
        sb.append("if(!window._map){window._map=L.map('map').setView([5.6037,-0.1870],12);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(window._map)}");
        sb.append("if(window._markers){window._markers.forEach(function(m){m.remove()})}");
        sb.append("window._markers=(drivers||[]).map(function(d){var m=L.marker([d.lat,d.lng]).addTo(window._map);var t='<b>'+ (d.name||'') +'</b><br/>'+ (d.vehicleType||'') +' • '+ (d.vehiclePlate||'') +'<br/>'+ (d.status||'');m.bindPopup(t);return m});");
        sb.append("var list=document.getElementById('list');if(list){list.innerHTML=(drivers||[]).map(function(d){return '<div class=\"driver\">'+ (d.name||'') +' • '+ (d.vehicleType||'') +' • '+ (d.status||'') +'</div>'}).join('')}},[drivers]);");
        sb.append("return e('div',null);");
        sb.append("}");
        sb.append("ReactDOM.createRoot(document.body.appendChild(document.createElement('div'))).render(e(App));");
        sb.append("})();");
        sb.append("</script>");
        sb.append("</body></html>");
        return createResponse(200, "OK", "text/html", sb.toString());
    }
    
    private String handleRides(String requestBody) {
        String response = "{\"rides\": []}";
        return createResponse(200, "OK", "application/json", response);
    }
    
    private String handleDrivers(String requestBody) {
        List<Driver> drivers = DriverRegistry.getInstance().getDrivers();
        
        StringBuilder sb = new StringBuilder();
        sb.append("{\"drivers\":[");
        
        for (int i = 0; i < drivers.size(); i++) {
            Driver d = drivers.get(i);
            sb.append("{")
              .append("\"id\":").append(d.getId()).append(",")
              .append("\"name\":\"").append(escape(d.getName())).append("\",")
              .append("\"online\":").append(d.isOnline()).append(",")
              .append("\"status\":\"").append(escape(d.getStatus())).append("\"")
              .append("}");
            
            if (i < drivers.size() - 1) sb.append(",");
        }
        
        sb.append("]}");
        
        return createResponse(200, "OK", "application/json", sb.toString());
    }
    
    private String handleDriverLocation(String requestBody) {
        try {
            List<Driver> activeDrivers = DriverRegistry.getInstance().getActiveDriversWithLocations();
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\"drivers\":[");
            
            for (int i = 0; i < activeDrivers.size(); i++) {
                Driver d = activeDrivers.get(i);
                sb.append("{")
                  .append("\"id\":").append(d.getId()).append(",")
                  .append("\"name\":\"").append(escape(d.getName())).append("\",")
                  .append("\"phone\":\"").append(escape(d.getPhone() != null ? d.getPhone() : "")).append("\",")
                  .append("\"lat\":").append(d.getLocation().getLatitude()).append(",")
                  .append("\"lng\":").append(d.getLocation().getLongitude()).append(",")
                  .append("\"vehicleType\":\"").append(escape(d.getVehicleType())).append("\",")
                  .append("\"vehiclePlate\":\"").append(escape(d.getVehiclePlate())).append("\",")
                  .append("\"status\":\"").append(escape(d.getStatus())).append("\"")
                  .append("}");
                
                if (i < activeDrivers.size() - 1) sb.append(",");
            }
            
            sb.append("]}");
            
            return createResponse(200, "OK", "application/json", sb.toString());
            
        } catch (Exception e) {
            System.err.println("Error getting driver locations: " + e.getMessage());
            return createResponse(500, "Internal Server Error", "application/json", 
                "{\"error\":\"Internal server error\"}");
        }
    }
    
    private String handleEvents(String requestBody) {
        List<ActivityEntry> events = ActivityLog.getInstance().getEvents();
        
        StringBuilder sb = new StringBuilder();
        sb.append("{\"events\":[");
        
        for (int i = 0; i < events.size(); i++) {
            ActivityEntry event = events.get(i);
            sb.append("{")
              .append("\"timestamp\":").append(event.getTimestamp()).append(",")
              .append("\"actor\":\"").append(escape(event.getActor())).append("\",")
              .append("\"action\":\"").append(escape(event.getAction())).append("\",")
              .append("\"detail\":\"").append(escape(event.getDetail())).append("\"")
              .append("}");
            
            if (i < events.size() - 1) sb.append(",");
        }
        
        sb.append("]}");
        
        return createResponse(200, "OK", "application/json", sb.toString());
    }
    
    private String handleAuditTrail(String requestBody) {
        List<AuditEntry> entries = ActivityLog.getInstance().getAuditTrail();
        
        StringBuilder sb = new StringBuilder();
        sb.append("{\"audit\":[");
        
        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);
            sb.append("{")
              .append("\"timestamp\":").append(entry.getTimestamp()).append(",")
              .append("\"actor\":\"").append(escape(entry.getActor())).append("\",")
              .append("\"action\":\"").append(escape(entry.getAction())).append("\",")
              .append("\"detail\":\"").append(escape(entry.getDetail())).append("\",")
              .append("\"ipAddress\":\"").append(escape(entry.getIpAddress())).append("\",")
              .append("\"userAgent\":\"").append(escape(entry.getUserAgent())).append("\"")
              .append("}");
            
            if (i < entries.size() - 1) sb.append(",");
        }
        
        sb.append("]}");
        
        return createResponse(200, "OK", "application/json", sb.toString());
    }
    
    private String handleLiveDrivers(String requestBody) {
        try {
            List<Driver> activeDrivers = DriverRegistry.getInstance().getActiveDriversWithLocations();
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\"drivers\":[");
            
            for (int i = 0; i < activeDrivers.size(); i++) {
                Driver d = activeDrivers.get(i);
                sb.append("{")
                  .append("\"id\":").append(d.getId()).append(",")
                  .append("\"name\":\"").append(escape(d.getName())).append("\",")
                  .append("\"phone\":\"").append(escape(d.getPhone() != null ? d.getPhone() : "")).append("\",")
                  .append("\"lat\":").append(d.getLocation().getLatitude()).append(",")
                  .append("\"lng\":").append(d.getLocation().getLongitude()).append(",")
                  .append("\"bearing\":").append(d.getBearing()).append(",")
                  .append("\"vehicleType\":\"").append(escape(d.getVehicleType())).append("\",")
                  .append("\"vehiclePlate\":\"").append(escape(d.getVehiclePlate())).append("\",")
                  .append("\"status\":\"").append(escape(d.getStatus())).append("\",")
                  .append("\"lastUpdate\":").append(d.getLastLocationUpdate())
                  .append("}");
                
                if (i < activeDrivers.size() - 1) sb.append(",");
            }
            
            sb.append("]}");
            
            return createResponse(200, "OK", "application/json", sb.toString());
            
        } catch (Exception e) {
            System.err.println("Error getting live drivers: " + e.getMessage());
            return createResponse(500, "Internal Server Error", "application/json", 
                "{\"error\":\"Internal server error\"}");
        }
    }
    
    private String handleUpdateDriverLocation(String requestBody) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return createResponse(400, "Bad Request", "application/json", 
                    "{\"error\":\"Missing request body\"}");
            }
            
            // Extract driver ID, latitude, longitude, and bearing from the request
            long id = extractLong(requestBody, "id", -1);
            if (id == -1) {
                // Try to extract as string and convert
                String idStr = extractValue(requestBody, "id");
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    return createResponse(400, "Bad Request", "application/json", 
                        "{\"error\":\"Invalid driver ID\"}");
                }
            }
            
            double lat = extractDouble(requestBody, "lat", Double.NaN);
            double lng = extractDouble(requestBody, "lng", Double.NaN);
            double bearing = extractDouble(requestBody, "bearing", 0.0);
            
            if (id <= 0 || Double.isNaN(lat) || Double.isNaN(lng)) {
                return createResponse(400, "Bad Request", "application/json", 
                    "{\"error\":\"Missing required fields (id, lat, lng)\"}");
            }
            
            // Check if driver exists first
            Driver driver = DriverRegistry.getInstance().getDriverById(id);
            boolean driverExists = (driver != null);
            
            if (driverExists) {
                // Update the driver's location in the registry
                DriverRegistry.getInstance().updateDriverLocation(id, (float)lat, (float)lng, (int)bearing);
                // Update driver status to active
                DriverRegistry.getInstance().updateDriverStatus(id, "active");
                DriverRegistry.getInstance().updateDriverOnlineStatus(id, true);
                
                // Log the location update
                ActivityLog.getInstance().addEvent(
                    String.valueOf(id),
                    "DRIVER_LOCATION_UPDATE",
                    String.format("Driver location updated: %.6f, %.6f", lat, lng)
                );
                
                return createResponse(200, "OK", "application/json", 
                    "{\"success\":true,\"message\":\"Driver location updated successfully\"}");
            } else {
                return createResponse(404, "Not Found", "application/json", 
                    "{\"success\":false,\"message\":\"Driver not found\"}");
            }
            
        } catch (Exception e) {
            System.err.println("Error updating driver location: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, "Internal Server Error", "application/json", 
                "{\"success\":false,\"message\":\"Internal server error\"}");
        }
    }
    
    private double extractDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return defaultValue;
        try {
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private String handleDriverRegistration(String method, String requestBody) {
        if (!"POST".equalsIgnoreCase(method)) {
            return createResponse(405, "Method Not Allowed", "text/plain", "Method not allowed");
        }
        
        try {
            if (requestBody != null && !requestBody.isEmpty()) {
                long telegramId = extractLong(requestBody, "telegramId", 0);
                String name = extractValue(requestBody, "name");
                String phone = extractValue(requestBody, "phone");
                String vehicleType = extractValue(requestBody, "vehicleType");
                String vehiclePlate = extractValue(requestBody, "vehiclePlate");
                
                if (telegramId > 0 && !name.isEmpty()) {
                    // Register driver
                    Driver driver = new Driver(telegramId, name, phone, "");
                    driver.setVehicleType(vehicleType);
                    driver.setVehiclePlate(vehiclePlate);
                    driver.setStatus("active");
                    driver.setOnline(true);
                    
                    DriverRegistry.getInstance().addDriver(driver);
                    
                    String successResponse = String.format(
                        "{\"success\":true,\"driverId\":%d,\"message\":\"Driver registered successfully\"}", 
                        telegramId
                    );
                    
                    ActivityLog.getInstance().addEvent(
                        "System", 
                        "DRIVER_MANUAL_REGISTRATION", 
                        String.format("Manual driver registration: %s (ID: %d)", name, telegramId)
                    );
                    
                    return createResponse(200, "OK", "application/json", successResponse);
                } else {
                    return createResponse(400, "Bad Request", "application/json", 
                        "{\"success\":false,\"message\":\"Missing required fields\"}");
                }
            } else {
                return createResponse(400, "Bad Request", "application/json", 
                    "{\"success\":false,\"message\":\"Missing request body\"}");
            }
            
        } catch (Exception e) {
            System.err.println("Error registering driver: " + e.getMessage());
            return createResponse(500, "Internal Server Error", "application/json", 
                "{\"success\":false,\"message\":\"Internal server error\"}");
        }
    }
    
    private String createResponse(int statusCode, String statusText, String contentType, String body) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("; charset=utf-8\r\n");
        response.append("Access-Control-Allow-Origin: *\r\n");
        response.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
        response.append("\r\n");
        response.append(body);
        return response.toString();
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
    
    private String extractValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }
    
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
    }
    
    private void startPeriodicUpdates() {
        // Update driver locations every 5 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateDriverLocations();
            } catch (Exception e) {
                System.err.println("Error updating driver locations: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
        
        // Cleanup inactive drivers every minute
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupInactiveDrivers();
            } catch (Exception e) {
                System.err.println("Error cleaning up drivers: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private void updateDriverLocations() {
        // Get latest driver locations from registry and update dashboard state
        List<Driver> activeDrivers = DriverRegistry.getInstance().getDrivers().stream()
            .filter(d -> d.isOnline() && d.getLocation() != null)
            .collect(Collectors.toList());
            
        dashboardState.put("activeDrivers", activeDrivers);
        dashboardState.put("lastUpdate", System.currentTimeMillis());
    }
    
    private void cleanupInactiveDrivers() {
        long cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000); // 30 minutes
        
        DriverRegistry.getInstance().getDrivers().forEach(driver -> {
            if (driver.getLastLocationUpdate() < cutoffTime && driver.isOnline()) {
                // Mark as offline if no recent location update
                DriverRegistry.getInstance().updateDriverOnlineStatus(driver.getId(), false);
                DriverRegistry.getInstance().updateDriverStatus(driver.getId(), "inactive");
                
                ActivityLog.getInstance().addEvent(
                    "System", 
                    "DRIVER_TIMEOUT", 
                    "Driver " + driver.getName() + " marked offline due to inactivity"
                );
            }
        });
    }
    
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        if (serverThread != null) {
            try {
                serverThread.join(5000); // Wait up to 5 seconds for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for server thread to finish");
            }
        }
        System.out.println("Dashboard Server stopped");
    }
}
