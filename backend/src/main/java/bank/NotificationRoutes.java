package bank;

import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationRoutes {


    public static void list(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");


            String query = ex.getRequestURI().getQuery();
            boolean unreadOnly = query != null && query.contains("unreadOnly=true");

            String sql = "SELECT id, message, read, created_at FROM notifications WHERE user_id = ?";
            if (unreadOnly) {
                sql += " AND read = FALSE";
            }
            sql += " ORDER BY created_at DESC";


            List<Map<String, Object>> list = new ArrayList<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(Map.of(
                                "id", rs.getLong("id"),
                                "message", rs.getString("message"),
                                "read", rs.getBoolean("read"),
                                "createdAt", rs.getTimestamp("created_at").toInstant().toString()
                        ));
                    }
                }
            }

            Json.send(ex, 200, list);
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void markAllRead(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");


            Db.update("UPDATE notifications SET read = TRUE WHERE user_id = ?", userId);

            Json.send(ex, 200, Map.of("status", "success"));
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }




    public static void readOne(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");


            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            if (!body.containsKey("id")) {
                Json.send(ex, 400, Map.of("error", "Missing required fields"));
                return;
            }
            long id = Long.parseLong(String.valueOf(body.get("id")));

            Db.update("UPDATE notifications SET read = TRUE WHERE id = ? AND user_id = ?", id, userId);
            Json.send(ex, 200, Map.of("status", "success"));
        } catch (NumberFormatException e) {
            try { Json.send(ex, 400, Map.of("error", "Invalid notification id")); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}