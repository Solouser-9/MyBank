package bank;

import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Map;

public class OtpService {

    private static final SecureRandom random = new SecureRandom();


    public static void create(long userId, String purpose) throws Exception {
        // LAYER 3: Rate limiting - count recent OTP requests for this user and purpose.

        long recentCount;
        try (Connection c = Db.conn()) {
            recentCount = Db.scalar(c,
                    "SELECT COUNT(*) FROM otp_codes WHERE user_id = ? AND purpose = ? AND created_at > now() - interval '15 minutes'",
                    userId, purpose);
        }

        if (recentCount >= 3) {
            throw new RateLimitException("Too many codes requested. Try again in a few minutes.");
        }

        // LAYER 3: OTP security - generate a six-digit code and hash it with the secret pepper.

        String code = String.format("%06d", random.nextInt(1000000));
        String pepperedHash = Crypto.sha256(code + Config.get("OTP_PEPPER"));

        Db.update("INSERT INTO otp_codes (user_id, purpose, code_hash, expires_at) VALUES (?, ?, ?, now() + interval '10 minutes')",
                userId, purpose, pepperedHash);


        String destination = "";

        try {
            Map<String, Object> userRow = Db.one(
                    "SELECT email FROM users WHERE id = ?", userId);
            if (userRow != null && userRow.get("email") != null) {
                destination = userRow.get("email").toString();
            }
        } catch (Exception e) {
            System.err.println("[OTP EMAIL LOOKUP FAILED] Continuing with fallback delivery.");
        }

        try {
            OtpDeliveryFactory.get().deliver(userId, destination, code, purpose);
        } catch (Exception e) {
            System.err.println("[OTP DELIVERY FAILED] user=" + userId
                    + " purpose=" + purpose + " - falling back to console. Error: "
                    + e.getMessage());
            System.out.println("[TEST OTP] user=" + userId + " purpose=" + purpose + " code=" + code);
        }
    }


    public static void verify(long userId, String purpose, String code) throws Exception {
        String pepperedHash = Crypto.sha256(code + Config.get("OTP_PEPPER"));


        Map<String, Object> row = Db.one(
                "SELECT id, code_hash, attempts FROM otp_codes WHERE user_id = ? AND purpose = ? AND used = FALSE AND expires_at > now() ORDER BY created_at DESC LIMIT 1",
                userId, purpose);

        if (row == null) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        long id = (long) row.get("id");
        int attempts = (int) row.get("attempts");


        if (attempts >= 5) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        String dbHash = (String) row.get("code_hash");


        if (Crypto.secureCompare(pepperedHash, dbHash)) {
            // LAYER 3: One-time OTP claim - the conditional update allows only one successful verifier.
            int claimed = Db.update(
                    "UPDATE otp_codes SET used = TRUE WHERE id = ? AND used = FALSE",
                    id);
            if (claimed == 0) {
                throw new IllegalArgumentException("Invalid or expired code");
            }
        } else {
            Db.update("UPDATE otp_codes SET attempts = attempts + 1 WHERE id = ?", id);
            throw new IllegalArgumentException("Invalid or expired code");
        }
    }
}