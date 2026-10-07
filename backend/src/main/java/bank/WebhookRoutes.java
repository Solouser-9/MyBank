package bank;

import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class WebhookRoutes {


    @SuppressWarnings("unchecked")
    public static void paystack(HttpExchange ex) {
        try {

            byte[] rawBytes = Json.raw(ex);
            String sigHeader = ex.getRequestHeaders().getFirst("x-paystack-signature");


            if (sigHeader == null) {
                Json.send(ex, 401, Map.of("error", "Missing signature"));
                return;
            }

            // LAYER 3: Webhook signature - authenticate the exact raw request bytes with HMAC-SHA512.
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(Config.get("PAYSTACK_SECRET_KEY").getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(rawBytes);
            StringBuilder hex = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            if (!MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.UTF_8), sigHeader.getBytes(StandardCharsets.UTF_8))) {
                Json.send(ex, 401, Map.of("error", "Invalid signature"));
                return;
            }

            String payloadStr = new String(rawBytes, StandardCharsets.UTF_8);
            Map<String, Object> payload = Json.parse(payloadStr, Map.class);
            Map<String, Object> data = (Map<String, Object>) payload.get("data");


            String event = (String) payload.get("event");
            String eventId = event + ":" + (data != null && data.get("reference") != null
                    ? (String) data.get("reference")
                    : String.valueOf(payload.get("id")));

            // LAYER 3: Duplicate webhook protection - the unique event insert is the atomic idempotency claim.
            try {
                Db.update("INSERT INTO provider_events (provider, event_id, event_type, payload) VALUES ('paystack', ?, ?, ?::jsonb)",
                        eventId, event, payloadStr);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    Json.send(ex, 200, Map.of("status", "duplicate"));
                    return;
                }
                throw e;
            }

            // LAYER 3: Fast acknowledgment - respond before scheduling asynchronous event processing.
            Json.send(ex, 200, Map.of("status", "received"));

            Workers.submit(() -> processEvent(eventId, event, data));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("unchecked")
    public static void processEvent(String eventId, String event, Map<String, Object> data) {
        try {

            if ("charge.success".equals(event)) {

                String acctNum = null;
                if (data.get("dedicated_account") instanceof Map) {
                    acctNum = (String) ((Map<String, Object>) data.get("dedicated_account")).get("account_number");
                }
                if (acctNum == null && data.get("authorization") instanceof Map) {
                    acctNum = (String) ((Map<String, Object>) data.get("authorization")).get("receiver_account_number");
                }


                if (acctNum == null) {
                    Audit.ev(null, "webhook.unknown_account", "provider_events", Map.of("event_id", eventId), "failure", null, eventId);
                    markProcessed(eventId);
                    return;
                }

                Map<String, Object> va = Db.one("SELECT user_id FROM virtual_accounts WHERE account_number = ?", acctNum);
                if (va == null) {
                    Audit.ev(null, "webhook.unknown_account", "provider_events", Map.of("account_number", acctNum), "failure", null, eventId);
                    markProcessed(eventId);
                    return;
                }


                long userId = (long) va.get("user_id");
                long amountKobo = ((Number) data.get("amount")).longValue();
                String reference = (String) data.get("reference");

                LedgerService.creditUser(userId, amountKobo, "Paystack Funding", reference);
                Audit.ev(userId, "wallet.credited", "wallets", Map.of("amount", amountKobo, "reference", reference), "success", null, eventId);
            }

            else if ("transfer.success".equals(event)) {
                String reference = data.get("reference") == null ? null : String.valueOf(data.get("reference"));
                Map<String, Object> txn = Db.one("SELECT user_id, amount_kobo, counterparty FROM wallet_transactions WHERE reference = ?", reference);
                if (txn != null) {
                    try (Connection c = Db.conn()) {
                        c.setAutoCommit(false);
                        try {
                            int rows = Db.update(c, "UPDATE wallet_transactions SET status = 'successful' WHERE reference = ? AND status = 'pending'", reference);
                            if (rows == 1) {
                                long uid = (long) txn.get("user_id");
                                long kobo = (long) txn.get("amount_kobo");
                                String cp = (String) txn.get("counterparty");
                                String msg = String.format("₦%d.%02d delivered to %s", kobo / 100, kobo % 100, cp);
                                NotificationService.insert(c, uid, msg);
                            }
                            c.commit();
                        } catch (Exception e) {
                            c.rollback();
                            throw e;
                        }
                    }
                }
            }

            else if ("transfer.failed".equals(event) || "transfer.reversed".equals(event)) {
                String reference = data.get("reference") == null ? null : String.valueOf(data.get("reference"));
                Map<String, Object> txn = Db.one("SELECT user_id, amount_kobo, fee_kobo, status FROM wallet_transactions WHERE reference = ?", reference);
                if (txn != null && !"reversed".equals(txn.get("status"))) {
                    long uid = (long) txn.get("user_id");
                    long kobo = (long) txn.get("amount_kobo");
                    long fee = (long) txn.get("fee_kobo");
                    LedgerService.reverseDebit(uid, kobo, fee, reference);
                }
            }

            else {
                System.out.println("Unhandled webhook event: " + event);
            }

            markProcessed(eventId);
        } catch (Exception e) {
            System.err.println("Webhook processing failed for event " + eventId);
            e.printStackTrace();
        }
    }


    private static void markProcessed(String eventId) throws SQLException {
        Db.update("UPDATE provider_events SET processed = TRUE WHERE event_id = ? AND provider = 'paystack'", eventId);
    }
}