package bank;

import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class TransferRoutes {



    private static String validKey(HttpExchange ex) throws Exception {

        String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
        if (key == null || key.trim().isEmpty()) {
            Json.send(ex, 400, Map.of("error", "Idempotency-Key header required"));
            return null;
        }
        key = key.trim();
        if (key.length() > 60) {
            Json.send(ex, 400, Map.of("error", "Idempotency-Key too long (max 60 characters)"));
            return null;
        }
        return key;
    }




    private static String priorStatusForThisUser(String reference, long userId) throws Exception {

        try (Connection c = Db.conn();
             PreparedStatement ps = c.prepareStatement("SELECT status FROM wallet_transactions WHERE reference = ? AND user_id = ?")) {
            ps.setString(1, reference);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("status") : null;
            }
        }
    }



    private static String readNarration(Map<String, Object> body) {

        String raw = body.containsKey("narration") && body.get("narration") != null
                ? String.valueOf(body.get("narration")).trim()
                : "";
        return raw.length() > 100 ? raw.substring(0, 100) : raw;
    }


    public static void requestOtp(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            String purpose = String.valueOf(body.get("purpose"));


            if (!"transfer".equals(purpose) && !"beneficiary".equals(purpose)) {
                Json.send(ex, 400, Map.of("error", "Invalid purpose"));
                return;
            }

            OtpService.create(userId, purpose);
            Audit.ev(userId, "otp.sent", purpose, Map.of(), "success", null, (String) ex.getAttribute("requestId"));


            String msg = "email".equals(Config.get("OTP_CHANNEL"))
                    ? "Verification code sent to your email"
                    : "Code printed to server console (test SMS)";
            Json.send(ex, 200, Map.of("message", msg));
        } catch (IllegalArgumentException e) {
            try { Json.send(ex, 400, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void internalTransfer(HttpExchange ex) {
        try {
            long senderId = (Long) ex.getAttribute("userId");
            String reqId = (String) ex.getAttribute("requestId");

            String idempotencyKey = validKey(ex);
            if (idempotencyKey == null) return;


            String kycStatus = "";
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT kyc_status FROM users WHERE id = ?")) {
                ps.setLong(1, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) kycStatus = rs.getString(1);
                }
            }

            if (!"VERIFIED".equals(kycStatus)) {
                Audit.ev(senderId, "kyc.blocked", "transfer", Map.of(), "failure", null, reqId);
                Json.send(ex, 403, Map.of("error", "Complete verification first"));
                return;
            }


            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            if (!body.containsKey("receiverId") || !body.containsKey("amountKobo") ||
                    !body.containsKey("pin") || !body.containsKey("otpCode")) {
                Json.send(ex, 400, Map.of("error", "Missing required fields"));
                return;
            }

            long receiverId = Long.parseLong(String.valueOf(body.get("receiverId")));
            long amountKobo = Long.parseLong(String.valueOf(body.get("amountKobo")));
            String pin = String.valueOf(body.get("pin"));
            String otpCode = String.valueOf(body.get("otpCode"));


            final String narration = readNarration(body);

            Validate.requireKobo(amountKobo, 1, Config.longOf("TRANSFER_CAP_KOBO"), "Amount");
            if (senderId == receiverId) {
                Json.send(ex, 400, Map.of("error", "Cannot transfer to self"));
                return;
            }


            String prior = priorStatusForThisUser(idempotencyKey, senderId);
            if (prior != null) {
                if ("reversed".equals(prior) || "failed".equals(prior)) {
                    Json.send(ex, 409, Map.of("error", "Previous attempt failed - start a new payment"));
                } else {
                    Json.send(ex, 200, Map.of("status", "already processed"));
                }
                return;
            }


            OtpService.verify(senderId, "transfer", otpCode);
            PinService.verify(senderId, pin);

            long balanceAfter = LedgerService.internalTransfer(senderId, receiverId, amountKobo, idempotencyKey, narration);

            if (balanceAfter == -1) {



                Json.send(ex, 409, Map.of("error", "Idempotency key already in use"));
                return;
            }


            Audit.ev(senderId, "transfer.success", "internal",
                    Map.of("receiver", receiverId, "amount", amountKobo, "narration", narration.isEmpty() ? "none" : narration),
                    "success", null, reqId);
            Json.send(ex, 200, Map.of("status", "successful", "balanceAfter", balanceAfter));

        } catch (IllegalArgumentException e) {
            try { Json.send(ex, 400, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void externalTransfer(HttpExchange ex) {
        try {
            long senderId = (Long) ex.getAttribute("userId");
            String reqId = (String) ex.getAttribute("requestId");

            String idempotencyKey = validKey(ex);
            if (idempotencyKey == null) return;


            String kycStatus = "";
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT kyc_status FROM users WHERE id = ?")) {
                ps.setLong(1, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) kycStatus = rs.getString(1);
                }
            }

            if (!"VERIFIED".equals(kycStatus)) {
                Audit.ev(senderId, "kyc.blocked", "transfer", Map.of(), "failure", null, reqId);
                Json.send(ex, 403, Map.of("error", "Complete verification first"));
                return;
            }


            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            if (!body.containsKey("accountNumber") || !body.containsKey("bankCode") ||
                    !body.containsKey("amountKobo") || !body.containsKey("pin") || !body.containsKey("otpCode")) {
                Json.send(ex, 400, Map.of("error", "Missing required fields"));
                return;
            }

            String accountNumber = String.valueOf(body.get("accountNumber"));
            String bankCode = String.valueOf(body.get("bankCode"));
            long amountKobo = Long.parseLong(String.valueOf(body.get("amountKobo")));
            String pin = String.valueOf(body.get("pin"));
            String otpCode = String.valueOf(body.get("otpCode"));


            final String narration = readNarration(body);

            Validate.requireKobo(amountKobo, 1, Config.longOf("TRANSFER_CAP_KOBO"), "Amount");


            String prior = priorStatusForThisUser(idempotencyKey, senderId);
            if (prior != null) {
                if ("reversed".equals(prior) || "failed".equals(prior)) {
                    Json.send(ex, 409, Map.of("error", "Previous attempt failed - start a new payment"));
                } else {
                    Json.send(ex, 200, Map.of("status", "already processed"));
                }
                return;
            }

            OtpService.verify(senderId, "transfer", otpCode);
            PinService.verify(senderId, pin);


            String resolvedName = ProviderFactory.get().resolveAccountName(accountNumber, bankCode);
            if (resolvedName == null) {
                Json.send(ex, 400, Map.of("error", "Could not verify account"));
                return;
            }


            long feeKobo = Config.longOf("FEE_KOBO");
            String counterparty = resolvedName + " - " + bankCode;

            boolean processed = LedgerService.debitForPayout(senderId, amountKobo, feeKobo, counterparty, idempotencyKey, "external");
            if (!processed) {
                Json.send(ex, 409, Map.of("error", "Idempotency key already in use"));
                return;
            }


            final String fNarration = narration;
            Workers.submit(() -> {
                try {
                    ProviderFactory.get().createTransfer(resolvedName, accountNumber, bankCode, amountKobo, idempotencyKey, fNarration);
                } catch (Exception e) {
                    Audit.ev(senderId, "transfer.provider_error", idempotencyKey,
                            Map.of("error", e.getMessage(), "narration", fNarration.isEmpty() ? "none" : fNarration),
                            "failure", null, reqId);
                }
            });

            Json.send(ex, 200, Map.of("status", "pending", "message", "Transfer initiated"));

        } catch (IllegalArgumentException e) {
            try { Json.send(ex, 400, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}