package bank;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class NotificationService {



    public static void insert(Connection c, long userId, String message) throws SQLException {
        Db.update(c, "INSERT INTO notifications (user_id, message) VALUES (?, ?)", userId, message);
    }


    public static List<Map<String, Object>> list(long userId, boolean unreadOnly) throws SQLException {
        if (unreadOnly) {
            return Db.list("SELECT id, message, read, created_at FROM notifications WHERE user_id = ? AND read = FALSE ORDER BY created_at DESC", userId);
        }
        return Db.list("SELECT id, message, read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC", userId);
    }


    public static void markAllRead(long userId) throws SQLException {
        Db.update("UPDATE notifications SET read = TRUE WHERE user_id = ? AND read = FALSE", userId);
    }
}