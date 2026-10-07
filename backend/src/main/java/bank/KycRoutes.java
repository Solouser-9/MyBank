package bank;

import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

public class KycRoutes {

    private static final Provider provider = ProviderFactory.get();


    public static void submit(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");
            String reqId = (String) ex.getAttribute("requestId");

            Db.update("UPDATE users SET kyc_status = 'VERIFIED' WHERE id = ?", userId);
            Audit.ev(userId, "kyc.verified", "user", Map.of(), "success", null, reqId);


            Workers.submit(() -> {
                try {
                    provider.createCustomerAndVa(userId);
                } catch (Exception e) {
                    System.err.println("VA issuance failed: " + e.getMessage());
                }
            });


            Json.send(ex, 200, Map.of(
                    "kycStatus", "VERIFIED",
                    "message", "Your account number will appear shortly"
            ));
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}