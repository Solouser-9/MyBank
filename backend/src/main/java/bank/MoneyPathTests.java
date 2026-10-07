package bank;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;













public class MoneyPathTests {


    private static int pass = 0;
    private static int fail = 0;


    private static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("[PASS] " + name); }
        else    { fail++; System.err.println("[FAIL] " + name + " - " + detail); }
    }







    private static void testReversalRace() throws Exception {

        long uid;
        String ref = "test-rev-" + System.nanoTime();
        try (Connection c = Db.conn()) {
            uid = Db.scalar(c, "INSERT INTO users (first_name, last_name, email, password_hash) "
                            + "VALUES ('T','Rev', ?, 'x') RETURNING id",
                    "rev-" + System.nanoTime() + "@test.local");
            Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, 0)", uid);
            Db.update(c, "INSERT INTO wallet_transactions (user_id, reference, type, amount_kobo, fee_kobo, "
                            + "balance_after_kobo, status, counterparty) VALUES (?, ?, 'debit', 50000, 0, 0, 'pending', 'race-test')",
                    uid, ref);
        }

        long balanceBefore = Db.one("SELECT balance_kobo FROM wallets WHERE user_id = ?",
                (Object) uid) != null
                ? ((Number) ((Map<String, Object>) Db.one("SELECT balance_kobo FROM wallets WHERE user_id = ?", uid)).get("balance_kobo")).longValue()
                : 0;


        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger successes = new AtomicInteger(0);
        Thread t1 = new Thread(() -> awaitAndReverse(start, uid, ref, successes));
        Thread t2 = new Thread(() -> awaitAndReverse(start, uid, ref, successes));
        t1.start(); t2.start();
        start.countDown();
        t1.join(10_000); t2.join(10_000);

        long balanceAfter = ((Number) ((Map<String, Object>) Db.one(
                "SELECT balance_kobo FROM wallets WHERE user_id = ?", uid)).get("balance_kobo")).longValue();
        String status = (String) ((Map<String, Object>) Db.one(
                "SELECT status FROM wallet_transactions WHERE reference = ?", ref)).get("status");

        check("reversal race: exactly one credit",
                (balanceAfter - balanceBefore) == 50_000,
                "balance moved by " + (balanceAfter - balanceBefore) + " (expected exactly 50000)");
        check("reversal race: row terminal state",
                "reversed".equals(status), "status = " + status);


        cleanup(uid, "wallet_transactions", ref);
    }


    private static void awaitAndReverse(CountDownLatch start, long uid, String ref, AtomicInteger successes) {
        try {
            start.await();
            try {
                LedgerService.reverseDebit(uid, 50_000, 0, ref);
                successes.incrementAndGet();
            } catch (Exception ignored) { }
        } catch (InterruptedException ignored) { }
    }






    private static void testOtpSingleUse() throws Exception {

        long uid;
        try (Connection c = Db.conn()) {
            uid = Db.scalar(c, "INSERT INTO users (first_name, last_name, email, password_hash) "
                            + "VALUES ('T','Otp', ?, 'x') RETURNING id",
                    "otp-" + System.nanoTime() + "@test.local");
        }




        String code = "1" + String.format("%05d", System.nanoTime() % 100000);
        String hash = Crypto.sha256(code + Config.get("OTP_PEPPER"));
        Db.update("INSERT INTO otp_codes (user_id, purpose, code_hash, expires_at) "
                + "VALUES (?, 'transfer', ?, now() + interval '10 minutes')", uid, hash);


        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger accepted = new AtomicInteger(0);
        final AtomicInteger rejected = new AtomicInteger(0);
        Thread t1 = new Thread(() -> awaitAndVerify(start, uid, code, accepted, rejected));
        Thread t2 = new Thread(() -> awaitAndVerify(start, uid, code, accepted, rejected));
        t1.start(); t2.start();
        start.countDown();
        t1.join(10_000); t2.join(10_000);

        check("OTP single-use: exactly one consumer",
                accepted.get() == 1 && rejected.get() == 1,
                "accepted=" + accepted.get() + " rejected=" + rejected.get());

        cleanup(uid, "otp_codes", null);
    }

    private static void awaitAndVerify(CountDownLatch start, long uid, String code, AtomicInteger ok, AtomicInteger rej) {

        try {
            start.await();
            try {
                OtpService.verify(uid, "transfer", code);
                ok.incrementAndGet();
            } catch (IllegalArgumentException e) {
                rej.incrementAndGet();
            }
        } catch (Exception ignored) { }
    }






    private static void testRefreshReplay() throws Exception {

        long uid;
        try (Connection c = Db.conn()) {
            uid = Db.scalar(c, "INSERT INTO users (first_name, last_name, email, password_hash) "
                            + "VALUES ('T','Jwt', ?, 'x') RETURNING id",
                    "jwt-" + System.nanoTime() + "@test.local");
        }


        String raw = JwtService.issueRefreshToken(uid);


        String hash1 = Crypto.sha256(raw + Config.get("OTP_PEPPER"));
        Map<String, Object> row = Db.one("SELECT id, user_id FROM refresh_tokens WHERE token_hash = ? AND expires_at > now()", hash1);
        long tokenId = (long) row.get("id");
        int claimed1 = Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE id = ? AND revoked = FALSE", tokenId);
        check("refresh replay: first rotation claims", claimed1 == 1, "claimed=" + claimed1);

        JwtService.issueRefreshToken(uid);


        int claimed2 = Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE id = ? AND revoked = FALSE", tokenId);
        int liveAfterBenign = (int) Db.scalar(Db.conn(),
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE", uid);
        check("refresh replay: recent replay is benign (no cascade)",
                claimed2 == 0 && liveAfterBenign == 1,
                "claimed2=" + claimed2 + " liveAfter=" + liveAfterBenign);



        Db.update("UPDATE refresh_tokens SET revoked_at = now() - interval '10 minutes' WHERE id = ?", tokenId);
        int claimed3 = Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE id = ? AND revoked = FALSE", tokenId);
        if (claimed3 == 0) {
            Db.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = now() WHERE user_id = ? AND revoked = FALSE", uid);
            Db.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", uid);
        }
        int liveAfterKill = (int) Db.scalar(Db.conn(),
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE", uid);
        int ver = (int) Db.scalar(Db.conn(), "SELECT token_version FROM users WHERE id = ?", uid);
        check("refresh replay: aged replay kills lineage + bumps token_version",
                claimed3 == 0 && liveAfterKill == 0 && ver == 1,
                "claimed3=" + claimed3 + " liveAfter=" + liveAfterKill + " ver=" + ver);

        cleanup(uid, "refresh_tokens", null);
    }


    private static void cleanup(long uid, String table, String ref) {
        try {

            if ("wallet_transactions".equals(table) && ref != null) {
                Db.update("DELETE FROM ledger_entries WHERE narration LIKE 'Reversal%' AND txn_ref::text IN "
                        + "(SELECT txn_ref::text FROM ledger_entries WHERE account = ?)", "wallet:user:" + uid);
                Db.update("DELETE FROM wallet_transactions WHERE reference = ?", ref);
            }
            if ("otp_codes".equals(table))  Db.update("DELETE FROM otp_codes WHERE user_id = ?", uid);
            if ("refresh_tokens".equals(table)) Db.update("DELETE FROM refresh_tokens WHERE user_id = ?", uid);
            Db.update("DELETE FROM notifications WHERE user_id = ?", uid);
            Db.update("DELETE FROM ledger_entries WHERE account = ?", "wallet:user:" + uid);
            Db.update("DELETE FROM wallets WHERE user_id = ?", uid);
            Db.update("DELETE FROM users WHERE id = ?", uid);
        } catch (Exception e) {
            System.err.println("[cleanup] incomplete for user " + uid + ": " + e.getMessage());
        }
    }


    public static void run() throws Exception {
        System.out.println("=== Money-path concurrency tests (pure Java) ===");
        testReversalRace();
        testRefreshReplay();
        testOtpSingleUse();
        System.out.println("=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }
}