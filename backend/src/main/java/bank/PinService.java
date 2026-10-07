package bank;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

public class PinService {


    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;


    public static void setPin(long userId, String pin) throws Exception {
        String hashed = BCrypt.hashpw(pin, BCrypt.gensalt());
        // LAYER 3: Lockout reset - setting a new PIN clears prior strikes and lock time.
        Db.update("UPDATE users SET pin_hash = ?, pin_attempts = 0, pin_locked_until = NULL WHERE id = ?",
                hashed, userId);
    }


    public static void verify(long userId, String pin) throws Exception {
        Map<String, Object> user = Db.one(
                "SELECT pin_hash, pin_locked_until FROM users WHERE id = ?", userId);


        if (user == null || user.get("pin_hash") == null) {
            throw new IllegalArgumentException("Set a transaction PIN first");
        }

        Object lockedUntil = user.get("pin_locked_until");
        // LAYER 3: Lockout check - reject attempts while the stored timeout is still in the future.
        if (lockedUntil instanceof java.sql.Timestamp
                && ((java.sql.Timestamp) lockedUntil).toInstant().isAfter(java.time.Instant.now())) {
            throw new IllegalArgumentException("Too many incorrect attempts. Try again in " + LOCK_MINUTES + " minutes.");
        }

        String hash = (String) user.get("pin_hash");
        if (!BCrypt.checkpw(pin, hash)) {
            // LAYER 3: Atomic strike counter - increment attempts and apply lockout in one SQL update.
            Db.update("UPDATE users SET "
                    + "pin_attempts = COALESCE(pin_attempts, 0) + 1, "
                    + "pin_locked_until = CASE WHEN COALESCE(pin_attempts, 0) + 1 >= ? "
                    + "THEN now() + (? * interval '1 minute') ELSE pin_locked_until END "
                    + "WHERE id = ?", MAX_ATTEMPTS, LOCK_MINUTES, userId);
            throw new IllegalArgumentException("Incorrect PIN");
        }


        Db.update("UPDATE users SET pin_attempts = 0, pin_locked_until = NULL WHERE id = ?", userId);
    }
}