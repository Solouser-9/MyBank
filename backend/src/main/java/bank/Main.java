package bank;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        try {

            Config.load();


            String[] requiredVars = {
                    "DB_URL", "DB_USER", "DB_PASS", "JWT_SECRET", "OTP_PEPPER",
                    "PAYSTACK_SECRET_KEY", "TRANSFER_CAP_KOBO", "FEE_KOBO", "CORS_ORIGIN",
                    "OTP_CHANNEL"
            };

            for (String req : requiredVars) {
                try {
                    Config.get(req);
                } catch (Exception e) {
                    System.err.println("FATAL: Missing mandatory environment variable: " + req);
                    System.exit(1);
                }
            }


            if ("email".equals(Config.get("OTP_CHANNEL"))) {
                for (String req : new String[]{"BREVO_API_KEY", "BREVO_SENDER_EMAIL"}) {
                    try {
                        Config.get(req);
                    } catch (Exception e) {
                        System.err.println("FATAL: OTP_CHANNEL=email requires " + req);
                        System.exit(1);
                    }
                }
            }


            if (Config.get("JWT_SECRET").length() < 32) {
                System.err.println("FATAL: JWT_SECRET must be at least 32 characters long.");
                System.exit(1);
            }

            Db.init();


            try (Connection c = Db.conn()) {
                Db.scalar(c, "SELECT 1");
                System.out.println("Database connection successful.");
            } catch (Exception e) {
                System.err.println("FATAL: Database ping failed: " + e.getMessage());
                System.exit(1);
            }


            // Auto-seed: runs ONCE per database (guarded by the seed_marker claim
            // inside Seed.run). Later boots see the marker and skip silently.
            Seed.run();


            if (args.length > 0 && args[0].equals("test")) {
                MoneyPathTests.run();
                System.exit(0);
            }

            Router.registerAll();


            Scheduler.start();


            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.createContext("/", Middleware::handle);
            server.start();

            System.out.println("Server started on port " + port + " (mode: " + (args.length > 0 ? args[0] : "server") + ")");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}