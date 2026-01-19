import java.util.Map;

/**
 * Main.java
 * 
 * Responsible for initializing the application
 * 
 * @author gusanthiago
 * @author hmmoreira
 */
public class Main {

	public static RideModel model;
	
	public static void main(String[] args) {
		model = RideModel.getInstance();
		initializeModels(model);
		boolean enableDashboard = false;
		boolean onlyDashboard = false;
		try {
			String s = System.getenv("RUN_DASHBOARD");
			if (s == null || s.length() == 0) {
				try {
					java.io.File f1 = new java.io.File(".env");
					if (f1.exists()) {
						java.util.List<String> lines = java.nio.file.Files.readAllLines(f1.toPath(), java.nio.charset.StandardCharsets.UTF_8);
						for (String line : lines) {
							String l = line.trim();
							if (l.startsWith("RUN_DASHBOARD=")) {
								String[] parts = l.split("=", 2);
								if (parts.length == 2) s = parts[1].trim();
							}
						}
					}
				} catch (Exception e2) {}
			}
			enableDashboard = "true".equalsIgnoreCase(s);
		} catch (Exception e) {}
		try {
			String s2 = System.getenv("RUN_ONLY_DASHBOARD");
			if (s2 == null || s2.length() == 0) {
				try {
					java.io.File f1 = new java.io.File(".env");
					if (f1.exists()) {
						java.util.List<String> lines = java.nio.file.Files.readAllLines(f1.toPath(), java.nio.charset.StandardCharsets.UTF_8);
						for (String line : lines) {
							String l = line.trim();
							if (l.startsWith("RUN_ONLY_DASHBOARD=")) {
								String[] parts = l.split("=", 2);
								if (parts.length == 2) s2 = parts[1].trim();
							}
						}
					}
				} catch (Exception e2) {}
			}
			onlyDashboard = "true".equalsIgnoreCase(s2);
		} catch (Exception e) {}
		if (enableDashboard || onlyDashboard) {
			int port = 8088;
			try {
				String p = System.getenv("PORT");
				if (p == null || p.length() == 0) {
					p = System.getenv("DASHBOARD_PORT");
				}
				if (p == null || p.length() == 0) {
					try {
						java.io.File f1 = new java.io.File(".env");
						if (f1.exists()) {
							java.util.List<String> lines = java.nio.file.Files.readAllLines(f1.toPath(), java.nio.charset.StandardCharsets.UTF_8);
							for (String line : lines) {
								String l = line.trim();
								if (l.startsWith("DASHBOARD_PORT=")) {
									String[] parts = l.split("=", 2);
									if (parts.length == 2) p = parts[1].trim();
								}
							}
						}
					} catch (Exception e2) {}
				}
				if (p != null && p.length() > 0) port = Integer.parseInt(p);
			} catch (Exception e) {}
			try { new DashboardServer(port).start(); } catch (Exception e) { System.out.println("Dashboard error: "+e.getMessage()+" (set DASHBOARD_PORT to a free port)"); }
			if (onlyDashboard) {
				return;
			}
		} else {
			System.out.println("Dashboard disabled (set RUN_DASHBOARD=true to enable).");
		}
		// Initialize Telegram Bot
		String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
		if (botToken == null || botToken.trim().isEmpty()) {
			java.io.File envFile = new java.io.File(".env");
			if (envFile.exists()) {
				try {
					java.util.Properties props = new java.util.Properties();
					props.load(new java.io.FileReader(envFile));
					botToken = props.getProperty("TELEGRAM_BOT_TOKEN");
				} catch (Exception e) {}
			}
		}
		if (botToken == null || botToken.trim().isEmpty()) {
			java.io.File envProdFile = new java.io.File(".env.production");
			if (envProdFile.exists()) {
				try {
					java.util.Properties props = new java.util.Properties();
					props.load(new java.io.FileReader(envProdFile));
					botToken = props.getProperty("TELEGRAM_BOT_TOKEN");
				} catch (Exception e) {}
			}
		}
		
		if (botToken == null || botToken.trim().isEmpty()) {
			System.out.println("Error: TELEGRAM_BOT_TOKEN not set. Please set TELEGRAM_BOT_TOKEN environment variable or add to .env file");
			return;
		}
		
		com.pengrad.telegrambot.TelegramBot bot = new com.pengrad.telegrambot.TelegramBot(botToken);
		View view = new View(bot);
		view.setRideModel(model); // Set the model using setter
		model.registerObserver(view);
		startAutoStatusUpdater(view);
		view.receiveUsersMessages();

	}
	
	// TODO pegar userId do uber
	public static void initializeModels(RideModel model) {
		
	}

	private static void startAutoStatusUpdater(View view) {
		Thread t = new Thread(new Runnable() {
			public void run() {
				while (true) {
					try {
						Map<Long, TransportRide> rides = ActivityLog.getInstance().getRidesByChat();
						for (Map.Entry<Long, TransportRide> e : rides.entrySet()) {
							Long chatId = e.getKey();
							TransportRide ride = e.getValue();
							if (ride == null) continue;
							String previous = ride.getStatus();
							if (previous == null) continue;
							if (previous.equalsIgnoreCase("completed") || previous.equalsIgnoreCase("cancelled")) continue;
							TransportRide updated = model.selectRide(ride);
							if (updated == null || updated.getStatus() == null) continue;
							String current = updated.getStatus();
							if (!current.equalsIgnoreCase(previous)) {
								ActivityLog.getInstance().setCurrentRide(chatId, updated);
								String msg = "Your ride status - " + current;
								view.update(chatId, msg);
								String s = "Ride status update: " + current;
								if (updated.getProduct() != null) s += " • " + updated.getProduct().getDisplayName();
								if (updated.getDriverName() != null) s += " • Driver: " + updated.getDriverName();
								view.notifyGroup(s);
							}
						}
						Thread.sleep(15000);
					} catch (Exception ex) {
						try { Thread.sleep(15000); } catch (InterruptedException ie) {}
					}
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
 
	
	
}
