package bank;

import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class JwtService {

    private static final SecureRandom random = new SecureRandom();


    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(Config.get("JWT_SECRET").getBytes(StandardCharsets.UTF_8));
    }


    public static String issue(long userId, int tokenVersion) {
        long nowMillis = System.currentTimeMillis();
        // LAYER 3: Access-token expiration - set the JWT lifetime to 15 minutes.
        long expMillis = nowMillis + (15 * 60 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("ver", tokenVersion)
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(expMillis))
                .signWith(getKey())
                .compact();
    }


    public static String issueRefreshToken(long userId) throws Exception {
        // LAYER 3: Refresh-token security - persist only the peppered hash of the random token.
        String rawToken = UUID.randomUUID().toString() + "-" + random.nextLong();
        String hash = Crypto.sha256(rawToken + Config.get("OTP_PEPPER"));

        Db.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '30 days')",
                userId, hash);

        return rawToken;
    }


    public static void requireUser(HttpExchange ex) throws Exception {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new SecurityException("Please log in");
        }

        String token = auth.substring(7);


        final Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new SecurityException("Please log in");
        }


        long userId;
        int tokenVer;
        try {
            userId = Long.parseLong(claims.getSubject());
            tokenVer = claims.get("ver", Integer.class);
        } catch (Exception e) {
            throw new SecurityException("Please log in");
        }

        // LAYER 3: Token-version kill switch - reject deleted users and tokens with an outdated version.
        Long dbVer;
        try (Connection c = Db.conn()) {
            dbVer = Db.scalar(c, "SELECT token_version FROM users WHERE id = ?", userId);
        }
        if (dbVer == null || tokenVer != dbVer.longValue()) {
            throw new SecurityException("Please log in");
        }

        ex.setAttribute("userId", userId);
    }
}