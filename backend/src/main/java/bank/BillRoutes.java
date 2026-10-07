package bank;

import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public class BillRoutes {

    private static final BillPaymentAdapter adapter = new TestBillPaymentAdapter();

    private static final List<String> VALID_BILLERS = List.of("airtime", "data", "electricity", "tv", "cable");


    public static void pay(HttpExchange ex) {
        try {
            long senderId = (Long) ex.getAttribute("userId");
            String reqId = (String) ex.getAttribute("requestId");





            String rawKey = ex.getRequestHeaders().getFirst("Idempotency-Key");
            if (rawKey == null || rawKey.trim().isEmpty()) {
                Json.send(ex, 400, Map.of("error", "Idempotency-Key header required"));
                return;
            }
            final String idempotencyKey = rawKey.trim();
            if (idempotencyKey.length() > 60) {
                Json.send(ex, 400, Map.of("error", "Idempotency-Key too long (max 60 characters)"));
                return;
            }


            String kycStatus = "";
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT kyc_status FROM users WHERE id = ?")) {
                ps.setLong(1, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) kycStatus = rs.getString(1);
                }
            }

            if (!"VERIFIED".equals(kycStatus)) {
                Audit.ev(senderId, "kyc.blocked", "bill", Map.of(), "failure", null, reqId);
                Json.send(ex, 403, Map.of("error", "Complete verification first"));
                return;
            }


            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            if (!body.containsKey("biller") || !body.containsKey("customerRef") ||
                    !body.containsKey("amountKobo") || !body.containsKey("pin") || !body.containsKey("otpCode")) {
                Json.send(ex, 400, Map.of("error", "Missing required fields"));
                return;
            }

            String biller = String.valueOf(body.get("biller"));
            String customerRef = String.valueOf(body.get("customerRef"));
            long amountKobo = Long.parseLong(String.valueOf(body.get("amountKobo")));
            String pin = String.valueOf(body.get("pin"));
            String otpCode = String.valueOf(body.get("otpCode"));


            if (!VALID_BILLERS.contains(biller)) {
                Json.send(ex, 400, Map.of("error", "Invalid biller"));
                return;
            }

            Validate.requireKobo(amountKobo, 1, Config.longOf("TRANSFER_CAP_KOBO"), "Amount");





            Map<String, Object> prior = Db.one(
                    "SELECT status FROM wallet_transactions WHERE reference = ? AND user_id = ?", idempotencyKey, senderId);
            if (prior != null) {
                String st = String.valueOf(prior.get("status"));
                if ("reversed".equals(st) || "failed".equals(st)) {
                    Json.send(ex, 409, Map.of("error", "Previous attempt failed - start a new payment"));
                } else {
                    Json.send(ex, 200, Map.of("status", "already processed"));
                }
                return;
            }


            OtpService.verify(senderId, "transfer", otpCode);
            PinService.verify(senderId, pin);

            String counterparty = biller + ":" + customerRef;



            boolean processed = LedgerService.debitForPayout(senderId, amountKobo, 0, counterparty, idempotencyKey, "bills");
            if (!processed) {
                Json.send(ex, 409, Map.of("error", "Idempotency key already in use"));
                return;
            }





            Workers.submit(() -> {
                try {
                    boolean success = adapter.pay(biller, customerRef, amountKobo, idempotencyKey);
                    if (success) {


                        try (Connection c = Db.conn()) {
                            c.setAutoCommit(false);
                            int rows = Db.update(c, "UPDATE wallet_transactions SET status = 'successful' WHERE reference = ? AND status = 'pending'", idempotencyKey);
                            if (rows == 1) {
                                NotificationService.insert(c, senderId, String.format("₦%.2f bill payment confirmed", amountKobo / 100.0));
                            }
                            c.commit();
                        }
                        System.out.println("[BillWorker] ref=" + idempotencyKey + " confirmed successful");
                    } else {


                        LedgerService.reverseDebit(senderId, amountKobo, 0, idempotencyKey);
                        Audit.ev(senderId, "bill.reversed", idempotencyKey, Map.of("biller", biller, "customerRef", customerRef), "failure", null, reqId);
                        System.out.println("[BillWorker] ref=" + idempotencyKey + " rejected by provider - funds returned");
                    }
                } catch (Exception e) {
                    System.err.println("Bill payment worker failed for ref=" + idempotencyKey + ": " + e.getMessage());
                }
            });

            Audit.ev(senderId, "bill.submitted", counterparty, Map.of("amount", amountKobo), "success", null, reqId);
            Json.send(ex, 200, Map.of("status", "pending", "message", "Bill payment initiated"));

        } catch (IllegalArgumentException e) {
            try { Json.send(ex, 400, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}