package bank;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Provider provider = ProviderFactory.get();


    public static void start() {

        scheduler.scheduleAtFixedRate(Scheduler::sweep, 1, 5, TimeUnit.MINUTES);
    }


    public static void sweep() {
        try { round0ReplayWebhooks(); } catch (Exception e) { System.err.println("[Scheduler] Round 0 failed: " + e.getMessage()); }
        try { round1ChasePending(); }   catch (Exception e) { System.err.println("[Scheduler] Round 1 failed: " + e.getMessage()); }
        try { round2BalanceCheck(); }   catch (Exception e) { System.err.println("[Scheduler] Round 2 failed: " + e.getMessage()); }
        try { round3VaRetry(); }        catch (Exception e) { System.err.println("[Scheduler] Round 3 failed: " + e.getMessage()); }
        try { round4Housekeeping(); }   catch (Exception e) { System.err.println("[Scheduler] Round 4 failed: " + e.getMessage()); }
        System.out.println("[Scheduler] Sweep complete.");
    }







    private static void round0ReplayWebhooks() {
        try {
            List<Map<String, Object>> events = Db.list(
                    "SELECT event_id, payload FROM provider_events " +
                            "WHERE processed = FALSE AND provider = 'paystack' " +
                            "AND received_at < now() - interval '5 minutes' " +
                            "AND received_at > now() - interval '1 day'");

            for (Map<String, Object> row : events) {
                String eventId = String.valueOf(row.get("event_id"));
                String payloadStr = String.valueOf(row.get("payload"));
                try {
                    Audit.ev(null, "webhook.replay", eventId,
                            Map.of("via", "watchman-replay"), "success", null, eventId);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) Json.parse(payloadStr, Map.class);
                    String eventType = String.valueOf(payload.get("event"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) payload.get("data");
                    WebhookRoutes.processEvent(eventId, eventType, data);
                } catch (Exception e) {
                    System.err.println("[Scheduler] Round 0 failed for " + eventId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Round 0 query failed: " + e.getMessage());
        }
    }


    private static void round1ChasePending() {
        try {


            List<Map<String, Object>> pending = Db.list(
                    "SELECT user_id, reference, amount_kobo, fee_kobo, counterparty FROM wallet_transactions "
                            + "WHERE status = 'pending' AND created_at < now() - interval '15 minutes' "
                            + "AND counterparty !~ '^(airtime|data|electricity|tv|cable):'");

            for (Map<String, Object> row : pending) {
                String ref = String.valueOf(row.get("reference"));
                long uid = ((Number) row.get("user_id")).longValue();
                long kobo = ((Number) row.get("amount_kobo")).longValue();
                long fee = ((Number) row.get("fee_kobo")).longValue();
                String counterparty = row.get("counterparty") == null ? "Unknown" : String.valueOf(row.get("counterparty"));
                try {
                    String status = provider.verifyTransfer(ref);
                    if ("successful".equalsIgnoreCase(status)) {

                        try (Connection c = Db.conn()) {
                            c.setAutoCommit(false);
                            try {




                                int rows = Db.update(c, "UPDATE wallet_transactions SET status = 'successful' WHERE reference = ? AND status = 'pending'", ref);
                                if (rows == 1) {
                                    NotificationService.insert(c, uid, String.format(
                                            "₦%d.%02d delivered to %s", kobo / 100, kobo % 100, counterparty));
                                }
                                c.commit();
                                Audit.ev(uid, "transfer.chase", ref,
                                        Map.of("via", "watchman",
                                                "action", rows == 1 ? "marked_successful" : "skipped_stale_status"),
                                        "success", null, ref);
                            } catch (Exception e) {
                                c.rollback();
                                throw e;
                            }
                        }
                    } else if ("failed".equalsIgnoreCase(status) || "reversed".equalsIgnoreCase(status)) {



                        boolean reversedNow = LedgerService.reverseDebit(uid, kobo, fee, ref);
                        Audit.ev(uid, "transfer.chase", ref,
                                Map.of("via", "watchman", "action", reversedNow ? "reversed" : "already_reversed"),
                                "success", null, ref);
                    }
                } catch (Exception e) {
                    System.err.println("[Scheduler] Round 1 failed for " + ref + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Round 1 query failed: " + e.getMessage());
        }
    }


    private static void round2BalanceCheck() {
        try {
            List<Map<String, Object>> mismatches = Db.list(
                    "SELECT w.user_id, w.balance_kobo, " +
                            "COALESCE(SUM(CASE WHEN l.direction = 'credit' THEN l.amount_kobo ELSE 0 END), 0) " +
                            " - COALESCE(SUM(CASE WHEN l.direction = 'debit' THEN l.amount_kobo ELSE 0 END), 0) AS ledger_bal " +
                            "FROM wallets w " +
                            "LEFT JOIN ledger_entries l ON l.account = 'wallet:user:' || w.user_id " +
                            "GROUP BY w.user_id, w.balance_kobo " +
                            "HAVING w.balance_kobo != (" +
                            "COALESCE(SUM(CASE WHEN l.direction = 'credit' THEN l.amount_kobo ELSE 0 END), 0) " +
                            " - COALESCE(SUM(CASE WHEN l.direction = 'debit' THEN l.amount_kobo ELSE 0 END), 0))");

            for (Map<String, Object> row : mismatches) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("ALARM: LEDGER MISMATCH! user " + row.get("user_id")
                        + " wallet=" + row.get("balance_kobo")
                        + " ledger=" + row.get("ledger_bal"));
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Round 2 failed: " + e.getMessage());
        }
    }


    private static void round3VaRetry() {
        try {
            List<Map<String, Object>> users = Db.list(
                    "SELECT id FROM users WHERE kyc_status = 'VERIFIED' AND id NOT IN (SELECT user_id FROM virtual_accounts) AND created_at < now() - interval '5 minutes'");

            for (Map<String, Object> row : users) {
                long uid = ((Number) row.get("id")).longValue();
                Audit.ev(uid, "va.retry", "user", Map.of("via", "watchman"), "success", null, "retry-" + uid);
                try {
                    provider.createCustomerAndVa(uid);
                } catch (Exception e) {
                    System.err.println("[Scheduler] Round 3 failed for user " + uid + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Round 3 failed: " + e.getMessage());
        }
    }






    private static void round4Housekeeping() {
        try {

            int otp = Db.update("DELETE FROM otp_codes WHERE expires_at < now() - interval '1 day'");
            int tokens = Db.update("DELETE FROM refresh_tokens WHERE expires_at < now() - interval '7 days' " +
                    "OR (revoked = TRUE AND created_at < now() - interval '7 days')");
            int events = Db.update("DELETE FROM provider_events WHERE processed = TRUE AND received_at < now() - interval '30 days'");


            int dead = Db.update("DELETE FROM provider_events WHERE processed = FALSE AND received_at < now() - interval '7 days'");
            if (otp + tokens + events + dead > 0) {
                System.out.println("[Scheduler] Housekeeping: pruned " + otp + " expired OTP codes, "
                        + tokens + " stale refresh tokens, " + events + " old provider events, "
                        + dead + " abandoned events");
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Round 4 failed: " + e.getMessage());
        }
    }
}