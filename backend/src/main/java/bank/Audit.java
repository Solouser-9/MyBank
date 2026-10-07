package bank;

import java.util.Map;

public class Audit {

    public static void ev(Long actorId, String action, String entity, Object details, String result, String ip, String requestId) {
        try {

            String detailsJson = details != null ? Json.write(details) : "{}";
            Db.update("INSERT INTO audit_logs (actor_id, actor_type, action, entity, details, ip, result, request_id) VALUES (?, 'user', ?, ?, ?::jsonb, ?, ?, ?)",
                    actorId, action, entity, detailsJson, ip, result, requestId);
        } catch (Exception e) {

            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }
}