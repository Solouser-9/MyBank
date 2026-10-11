package bank;

import com.sun.net.httpserver.HttpExchange;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Middleware {

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "/api/auth/register", "/api/auth/verify-otp", "/api/auth/login",
            "/api/auth/verify-login-otp", "/api/auth/refresh", "/api/health",
            "/webhooks/paystack"
    );


    private static final ConcurrentHashMap<String, AtomicInteger> rateLimits = new ConcurrentHashMap<>();
    private static volatile long currentWindowMinute = System.currentTimeMillis() / 60000;


    public static void handle(HttpExchange ex) {

        String requestId = UUID.randomUUID().toString();
        ex.setAttribute("requestId", requestId);
        String ip = getIp(ex);
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {

            if (!checkRateLimit(ip, path)) {
                Json.send(ex, 429, Map.of("error", "Too many requests"));
                return;
            }


            ex.getResponseHeaders().set("Access-Control-Allow-Origin", Config.get("CORS_ORIGIN"));
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key");
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");

            if (method.equalsIgnoreCase("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            // HEAD requests (health/platform probes): respond with headers
            // only — writing a body to a HEAD exchange makes the JDK server
            // throw (the "stream closed" traces in the Render logs).
            if (method.equalsIgnoreCase("HEAD")) {
                ex.sendResponseHeaders(200, -1);
                ex.close();
                return;
            }


            String routeKey = method + " " + path;
            if (!Router.routes.containsKey(routeKey)) {
                Json.send(ex, 404, Map.of("error", "Not found"));
                return;
            }


            if (!PUBLIC_ROUTES.contains(path)) {
                JwtService.requireUser(ex);
            }

            Router.routes.get(routeKey).accept(ex);

        } catch (SecurityException e) {
            // LAYER 3: Global error handling - map authentication and validation failures to safe HTTP responses.
            System.err.println("[401] " + method + " " + path + " - " + e.getMessage());
            Json.send(ex, 401, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {

            String msg = e.getMessage().contains("JSON") ? "Invalid JSON" : e.getMessage();
            System.err.println("[400] " + method + " " + path + " - " + msg);
            Json.send(ex, 400, Map.of("error", msg));
        } catch (RateLimitException e) {
            System.err.println("[429] " + method + " " + path + " - " + e.getMessage());
            Json.send(ex, 429, Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {

            if ("Request too large".equals(e.getMessage())) {
                Json.send(ex, 413, Map.of("error", "Request too large"));
            } else {
                e.printStackTrace();
                Json.send(ex, 500, Map.of("error", "Something went wrong"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Json.send(ex, 500, Map.of("error", "Something went wrong"));
        } finally {
            // LAYER 3: Guaranteed audit logging - this finally block records every request outcome.

            int code = ex.getResponseCode();
            String result = (code >= 200 && code < 400) ? "success" : "failure";
            if (code == -1) result = "failure";

            Long actorId = (Long) ex.getAttribute("userId");
            Audit.ev(actorId, "api.request", path, Map.of("method", method, "status", code), result, ip, requestId);
        }
    }


    private static boolean checkRateLimit(String ip, String path) {
        // LAYER 3: Rate limiting - clear request counts when the minute window rolls over.
        long window = System.currentTimeMillis() / 60000;
        if (window > currentWindowMinute) {
            rateLimits.clear();
            currentWindowMinute = window;
        }

        String category = "default";
        int limit = 120;


        if (path.startsWith("/api/auth/") || path.startsWith("/api/otp/")) {
            category = "auth";
            limit = 10;
        } else if (path.startsWith("/webhooks/")) {
            category = "webhook";
            limit = 600;
        }

        String key = ip + ":" + category;
        int count = rateLimits.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        return count <= limit;
    }


    private static String getIp(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }
}