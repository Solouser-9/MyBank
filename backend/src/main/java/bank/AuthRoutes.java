package bank;

import com.sun.net.httpserver.HttpExchange;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class AuthRoutes {


    public static void register(HttpExchange ex) {

        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();


        String firstName = Validate.requireNonBlank((String) body.get("firstName"), "First name");
        String lastName = Validate.requireNonBlank((String) body.get("lastName"), "Last name");
        String email = Validate.requireEmail((String) body.get("email"));
        String phone = Validate.requirePhone((String) body.get("phone"));
        String password = Validate.requireNonBlank((String) body.get("password"), "Password");

        // LAYER 3: Password security - validate strength and hash with BCrypt before persistence.

        Validate.requireStrongPassword(password, "Password");

        String passHash = BCrypt.hashpw(password, BCrypt.gensalt());
        long userId;


        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {
                userId = Db.scalar(c,
                        "INSERT INTO users (first_name, last_name, email, phone, password_hash) VALUES (?, ?, ?, ?, ?) RETURNING id",
                        firstName, lastName, email, phone, passHash);

                Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, 0)", userId);

                NotificationService.insert(c, userId, "Welcome to MyBank!");

                c.commit();
            } catch (SQLException e) {
                c.rollback();

                if ("23505".equals(e.getSQLState())) {
                    throw new IllegalArgumentException("Invalid details");
                }
                throw e;
            }
        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        try {
            OtpService.create(userId, "signup");
            Audit.ev(userId, "user.registered", "users", Map.of("email", email), "success", ip, reqId);
            String msg = "email".equals(Config.get("OTP_CHANNEL"))
                    ? "Verification code sent to your email"
                    : "Code printed to server console (test SMS)";
            Json.send(ex, 200, Map.of("userId", userId, "message", msg));
        } catch (RateLimitException e) {

            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void verifyOtp(HttpExchange ex) {

        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();

        long userId = Long.parseLong(Validate.requireNonBlank(String.valueOf(body.get("userId")), "User ID"));
        String code = Validate.requireDigits((String) body.get("code"), 6, 6, "OTP code");

        try {
            OtpService.verify(userId, "signup", code);
            Audit.ev(userId, "otp.verified", "otp_codes", Map.of("purpose", "signup"), "success", ip, reqId);
            Json.send(ex, 200, Map.of("verified", true));
        } catch (IllegalArgumentException e) {
            Audit.ev(userId, "otp.failed", "otp_codes", Map.of("purpose", "signup"), "failure", ip, reqId);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void login(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();

        String email = Validate.requireEmail((String) body.get("email"));
        String password = Validate.requireNonBlank((String) body.get("password"), "Password");

        try {

            Map<String, Object> user = Db.one("SELECT id, password_hash FROM users WHERE email = ?", email);
            if (user == null || !BCrypt.checkpw(password, (String) user.get("password_hash"))) {
                Audit.ev(null, "login.failed", "users", Map.of("email", email), "failure", ip, reqId);
                throw new SecurityException("Invalid details");
            }

            long userId = (long) user.get("id");
            OtpService.create(userId, "login");
            String msg = "email".equals(Config.get("OTP_CHANNEL"))
                    ? "A verification code has been sent to your email"
                    : "Code printed to server console (test SMS)";
            Json.send(ex, 200, Map.of("requiresOtp", true, "userId", userId, "message", msg));
        } catch (SecurityException | RateLimitException e) {

            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void verifyLoginOtp(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();

        long userId = Long.parseLong(Validate.requireNonBlank(String.valueOf(body.get("userId")), "User ID"));
        String code = Validate.requireDigits((String) body.get("code"), 6, 6, "OTP code");

        try {
            OtpService.verify(userId, "login", code);


            int tokenVersion;
            try (Connection c = Db.conn()) {
                tokenVersion = (int) Db.scalar(c, "SELECT token_version FROM users WHERE id = ?", userId);
            }

            String accessToken = JwtService.issue(userId, tokenVersion);
            String refreshToken = JwtService.issueRefreshToken(userId);

            Audit.ev(userId, "login.success", "users", Map.of(), "success", ip, reqId);
            Json.send(ex, 200, Map.of("accessToken", accessToken, "refreshToken", refreshToken, "userId", userId));
        } catch (IllegalArgumentException e) {
            Audit.ev(userId, "login.otp_failed", "otp_codes", Map.of("purpose", "login"), "failure", ip, reqId);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void refresh(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        String refreshToken = Validate.requireNonBlank((String) body.get("refreshToken"), "Refresh token");
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();

        try {

            String hash = Crypto.sha256(refreshToken + Config.get("OTP_PEPPER"));
            Map<String, Object> row = Db.one(
                    "SELECT id, user_id, revoked_at FROM refresh_tokens WHERE token_hash = ? AND expires_at > now()", hash);

            if (row == null) {
                throw new SecurityException("Invalid details");
            }

            long tokenId = (long) row.get("id");
            long userId = (long) row.get("user_id");

            // LAYER 3: Refresh-token rotation - atomically revoke a live token, then detect reuse.
            int claimed = Db.update(
                    "UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE id = ? AND revoked = FALSE", tokenId);

            if (claimed == 0) {

                Object revokedAt = row.get("revoked_at");
                boolean recentlyRevoked = revokedAt instanceof java.sql.Timestamp
                        && ((java.sql.Timestamp) revokedAt).toInstant()
                        .isAfter(java.time.Instant.now().minusSeconds(60));

                if (recentlyRevoked) {
                    Audit.ev(userId, "refresh.replay_benign", "refresh_tokens",
                            Map.of("token_id", tokenId), "failure", ip, reqId);
                    throw new SecurityException("Session refresh conflict - please log in again");
                }

                Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE user_id = ? AND revoked = FALSE", userId);
                Db.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", userId);
                Audit.ev(userId, "refresh.reuse_detected", "refresh_tokens",
                        Map.of("token_id", tokenId), "failure", ip, reqId);
                throw new SecurityException("Invalid details");
            }


            int tokenVersion;
            try (Connection c = Db.conn()) {
                tokenVersion = (int) Db.scalar(c, "SELECT token_version FROM users WHERE id = ?", userId);
            }

            String newAccess = JwtService.issue(userId, tokenVersion);
            String newRefresh = JwtService.issueRefreshToken(userId);

            Audit.ev(userId, "token.rotated", "refresh_tokens", Map.of(), "success", ip, reqId);
            Json.send(ex, 200, Map.of("accessToken", newAccess, "refreshToken", newRefresh));
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void setPin(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long userId = (long) ex.getAttribute("userId");

        String pin = Validate.requireDigits((String) body.get("pin"), 4, 6, "PIN");

        try {
            PinService.setPin(userId, pin);
            Audit.ev(userId, "pin.changed", "users", Map.of(), "success", ip, reqId);
            Json.send(ex, 200, Map.of("status", "PIN set"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void changePassword(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        String reqId = (String) ex.getAttribute("requestId");
        String ip = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ip == null) ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long userId = (long) ex.getAttribute("userId");

        String oldPassword = Validate.requireNonBlank((String) body.get("oldPassword"), "Old password");
        String newPassword = Validate.requireNonBlank((String) body.get("newPassword"), "New password");

        Validate.requireStrongPassword(newPassword, "New password");

        try {

            Map<String, Object> user = Db.one("SELECT password_hash, token_version FROM users WHERE id = ?", userId);
            if (user == null || !BCrypt.checkpw(oldPassword, (String) user.get("password_hash"))) {
                throw new SecurityException("Invalid details");
            }

            String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            // LAYER 3: Global logout - bump the token version and revoke all refresh tokens.
            Db.update("UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?", newHash, userId);
            Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE user_id = ?", userId);

            int newVersion = (int) user.get("token_version") + 1;
            String newAccess = JwtService.issue(userId, newVersion);

            Audit.ev(userId, "password.changed", "users", Map.of(), "success", ip, reqId);
            Json.send(ex, 200, Map.of("accessToken", newAccess));
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void logout(HttpExchange ex) {
        Map<String, Object> body = Json.body(ex);
        long userId = (long) ex.getAttribute("userId");
        String refreshToken = Validate.requireNonBlank((String) body.get("refreshToken"), "Refresh token");

        try {

            String hash = Crypto.sha256(refreshToken + Config.get("OTP_PEPPER"));
            Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE token_hash = ? AND user_id = ?", hash, userId);
            Json.send(ex, 200, Map.of("status", "Logged out"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}