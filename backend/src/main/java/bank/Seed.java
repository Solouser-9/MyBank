package bank;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.util.UUID;

public class Seed {

    public static void run() throws Exception {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {

                String pass = BCrypt.hashpw("password", BCrypt.gensalt());
                String pin = BCrypt.hashpw("1234", BCrypt.gensalt());



                Db.update(c, "INSERT INTO users (first_name, last_name, email, password_hash, pin_hash, kyc_status) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        "Ada", "Tester", "ada@test.com", pass, pin, "VERIFIED");
                long adaId = Db.scalar(c, "SELECT id FROM users WHERE email=?", "ada@test.com");


                if (!Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ?", adaId)) {
                    Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, ?)", adaId, 500000);


                    String txnRef = UUID.randomUUID().toString();
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:opening', 'debit', ?, 'Seed funding')", txnRef, 500000);
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'credit', ?, 'Seed funding')", txnRef, "wallet:user:" + adaId, 500000);
                }



                Db.update(c, "INSERT INTO users (first_name, last_name, email, password_hash, pin_hash, kyc_status) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        "Tunde", "Tester", "tunde@test.com", pass, pin, "PENDING");
                long tundeId = Db.scalar(c, "SELECT id FROM users WHERE email=?", "tunde@test.com");
                Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, ?) ON CONFLICT DO NOTHING", tundeId, 0);


                String realEmail = Config.getOptional("TEST_REAL_EMAIL");
                if (realEmail != null && !realEmail.isBlank()) {
                    String realPass = BCrypt.hashpw("password", BCrypt.gensalt());
                    String realPin = BCrypt.hashpw("1234", BCrypt.gensalt());
                    Db.update(c, "INSERT INTO users (first_name, last_name, email, phone, password_hash, pin_hash, kyc_status) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                            "Zainab", "Tester", realEmail, "+2348012345699", realPass, realPin, "PENDING");
                    long zid = Db.scalar(c, "SELECT id FROM users WHERE email=?", realEmail);
                    Db.update(c, "INSERT INTO wallets (user_id, balance_kobo) VALUES (?, 0) ON CONFLICT DO NOTHING", zid);
                    System.out.println("Zainab Tester (" + realEmail + ") ID: " + zid + ", PIN: 1234, Wallet: 0 kobo");
                }


                c.commit();
                System.out.println("Seed complete.");
                System.out.println("Ada Tester (ada@test.com) ID: " + adaId + ", PIN: 1234, Wallet: 500000 kobo");
                System.out.println("Tunde Tester (tunde@test.com) ID: " + tundeId + ", PIN: 1234, Wallet: 0 kobo");
            } catch (Exception e) {

                c.rollback();
                throw e;
            }
        }
    }
}