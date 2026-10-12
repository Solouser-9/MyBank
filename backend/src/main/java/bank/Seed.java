package bank;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.util.UUID;

public class Seed {

    public static void run() throws Exception {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {

                // ── Seed marker: the seed runs at most ONCE per database, ever. ──
                // The claim is an atomic conditional INSERT (the same pattern as the
                // OTP/refresh/reversal claims): only the first boot inserts the marker
                // row; every later boot sees it and skips — even if the seeded users
                // were deleted afterward. The marker check, insert, and all seed data
                // share ONE transaction, so a crash mid-seed rolls the marker back
                // too and the next boot retries cleanly.
                if (Db.exists(c, "SELECT 1 FROM seed_marker WHERE id = TRUE")) {
                    c.commit();
                    System.out.println("Seed marker found — skipping (database already seeded).");
                    return;
                }
                Db.update(c, "INSERT INTO seed_marker (id) VALUES (TRUE)");

                String pass = BCrypt.hashpw("Password123", BCrypt.gensalt());
                String pin = BCrypt.hashpw("1234", BCrypt.gensalt());

                // Real, receivable email addresses — in email mode, each user's
                // OTPs are delivered to their own inbox.
                Db.update(c, "INSERT INTO users (first_name, last_name, email, password_hash, pin_hash, kyc_status) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        "Ada", "Tester", "bakareabdussalam5@gmail.com", pass, pin, "VERIFIED");
                long adaId = Db.scalar(c, "SELECT id FROM users WHERE email=?", "bakareabdussalam5@gmail.com");

                if (!Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ?", adaId)) {
                    Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, ?)", adaId, 500000);

                    String txnRef = UUID.randomUUID().toString();
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:opening', 'debit', ?, 'Seed funding')", txnRef, 500000);
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'credit', ?, 'Seed funding')", txnRef, "wallet:user:" + adaId, 500000);
                }

                Db.update(c, "INSERT INTO users (first_name, last_name, email, password_hash, pin_hash, kyc_status) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        "Tunde", "Tester", "abdussalambakare7@gmail.com", pass, pin, "PENDING");
                long tundeId = Db.scalar(c, "SELECT id FROM users WHERE email=?", "abdussalambakare7@gmail.com");
                Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, ?) ON CONFLICT DO NOTHING", tundeId, 0);

                c.commit();
                System.out.println("Seed complete.");
                System.out.println("Ada Tester (bakareabdussalam5@gmail.com) ID: " + adaId + ", PIN: 1234, Wallet: 500000 kobo");
                System.out.println("Tunde Tester (abdussalambakare7@gmail.com) ID: " + tundeId + ", PIN: 1234, Wallet: 0 kobo");
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}