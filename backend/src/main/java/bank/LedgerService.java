package bank;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public class LedgerService {


    public static void creditUser(long uid, long kobo, String narration, String reference) {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {

                if (Db.exists(c, "SELECT 1 FROM wallet_transactions WHERE reference = ?", reference)) {
                    c.rollback();
                    return;
                }

                Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ? FOR UPDATE", uid);
                Db.update(c, "UPDATE wallets SET balance_kobo = balance_kobo + ? WHERE user_id = ?", kobo, uid);
                long bal = Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", uid);


                String txnRef = UUID.randomUUID().toString();
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:settlement', 'debit', ?, ?)", txnRef, kobo, narration);
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'credit', ?, ?)", txnRef, "wallet:user:" + uid, kobo, narration);

                Db.update(c, "INSERT INTO wallet_transactions (user_id, reference, type, amount_kobo, fee_kobo, balance_after_kobo, status, category, counterparty) VALUES (?, ?, 'credit', ?, 0, ?, 'successful', 'funding', 'Provider')", uid, reference, kobo, bal);

                NotificationService.insert(c, uid, String.format("₦%.2f credited", kobo / 100.0));
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static boolean debitForPayout(long uid, long kobo, long fee, String narration, String reference, String category) {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {

                if (Db.exists(c, "SELECT 1 FROM wallet_transactions WHERE reference = ?", reference)) {
                    c.rollback();
                    return false;
                }
                // LAYER 3: Double-spending lock - debitForPayout locks the wallet before reading its balance.
                Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ? FOR UPDATE", uid);
                long bal = Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", uid);
                long total = kobo + fee;
                if (bal < total) {
                    c.rollback();
                    throw new IllegalArgumentException("Insufficient balance");
                }
                Db.update(c, "UPDATE wallets SET balance_kobo = balance_kobo - ? WHERE user_id = ?", total, uid);
                long balAfter = bal - total;


                String txnRef = UUID.randomUUID().toString();
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'debit', ?, ?)", txnRef, "wallet:user:" + uid, total, narration);
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:settlement', 'credit', ?, ?)", txnRef, kobo, narration);
                if (fee > 0) {
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:fees', 'credit', ?, ?)", txnRef, fee, "Fee: " + narration);
                }

                Db.update(c, "INSERT INTO wallet_transactions (user_id, reference, type, amount_kobo, fee_kobo, balance_after_kobo, status, category, counterparty) VALUES (?, ?, 'debit', ?, ?, ?, 'pending', ?, ?)", uid, reference, kobo, fee, balAfter, category, narration);

                NotificationService.insert(c, uid, String.format("₦%.2f sent to %s - pending", kobo / 100.0, narration));
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;

                if (isUniqueViolation(e)) return false;
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static boolean reverseDebit(long uid, long kobo, long fee, String reference) {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {
                // LAYER 3: Safe refund - atomically claim only pending/successful debits for reversal.
                int claimed = Db.update(c,
                        "UPDATE wallet_transactions SET status = 'reversed' WHERE reference = ? AND status IN ('pending','successful') AND type = 'debit'",
                        reference);
                if (claimed == 0) {
                    c.rollback();
                    return false;
                }


                long realUid = Db.scalar(c, "SELECT user_id FROM wallet_transactions WHERE reference = ?", reference);
                long amt = Db.scalar(c, "SELECT amount_kobo FROM wallet_transactions WHERE reference = ?", reference);
                long feeRow = Db.scalar(c, "SELECT fee_kobo FROM wallet_transactions WHERE reference = ?", reference);
                long total = amt + feeRow;


                Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ? FOR UPDATE", realUid);
                Db.update(c, "UPDATE wallets SET balance_kobo = balance_kobo + ? WHERE user_id = ?", total, realUid);
                long balAfter = Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", realUid);

                String txnRef = UUID.randomUUID().toString();
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:settlement', 'debit', ?, 'Reversal')", txnRef, amt);
                if (feeRow > 0) {
                    Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, 'pool:fees', 'debit', ?, 'Fee Reversal')", txnRef, feeRow);
                }
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'credit', ?, 'Reversal')", txnRef, "wallet:user:" + realUid, total);

                NotificationService.insert(c, realUid, String.format("₦%.2f returned - payment reversed", total / 100.0));
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static long internalTransfer(long senderId, long receiverId, long amountKobo, String reference, String narration) {
        String note = (narration == null || narration.isEmpty())
                ? "" : " - " + narration;
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {

                if (Db.exists(c, "SELECT 1 FROM wallet_transactions WHERE reference = ?", reference)) {
                    c.rollback();
                    return -1;
                }

                // LAYER 3: Deadlock prevention - lock both wallets in ascending user ID order.
                long first = Math.min(senderId, receiverId);
                long second = Math.max(senderId, receiverId);
                Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ? FOR UPDATE", first);
                Db.exists(c, "SELECT 1 FROM wallets WHERE user_id = ? FOR UPDATE", second);

                if (Db.exists(c, "SELECT 1 FROM wallet_transactions WHERE reference = ?", reference)) {
                    c.rollback();
                    return -1;
                }


                long senderBal = Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", senderId);
                if (senderBal < amountKobo) {
                    c.rollback();
                    throw new IllegalArgumentException("Insufficient balance");
                }

                if (!Db.exists(c, "SELECT 1 FROM users WHERE id = ?", receiverId)) {
                    c.rollback();
                    throw new IllegalArgumentException("Receiver not found");
                }


                Db.update(c, "UPDATE wallets SET balance_kobo = balance_kobo - ? WHERE user_id = ?", amountKobo, senderId);
                Db.update(c, "UPDATE wallets SET balance_kobo = balance_kobo + ? WHERE user_id = ?", amountKobo, receiverId);

                long senderBalAfter = senderBal - amountKobo;
                long receiverBalAfter = Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", receiverId);

                String txnRef = UUID.randomUUID().toString();
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'debit', ?, ?)", txnRef, "wallet:user:" + senderId, amountKobo, "Internal transfer to User " + receiverId + note);
                Db.update(c, "INSERT INTO ledger_entries (txn_ref, account, direction, amount_kobo, narration) VALUES (?::uuid, ?, 'credit', ?, ?)", txnRef, "wallet:user:" + receiverId, amountKobo, "Internal transfer from User " + senderId + note);

                Db.update(c, "INSERT INTO wallet_transactions (user_id, reference, type, amount_kobo, fee_kobo, balance_after_kobo, status, category, counterparty) VALUES (?, ?, 'debit', ?, 0, ?, 'successful', 'internal', ?)", senderId, reference, amountKobo, senderBalAfter, "User " + receiverId);
                Db.update(c, "INSERT INTO wallet_transactions (user_id, reference, type, amount_kobo, fee_kobo, balance_after_kobo, status, category, counterparty) VALUES (?, ?, 'credit', ?, 0, ?, 'successful', 'internal', ?)", receiverId, reference + "-in", amountKobo, receiverBalAfter, "User " + senderId);

                NotificationService.insert(c, senderId, String.format("₦%.2f sent to User %d", amountKobo / 100.0, receiverId) + note);
                NotificationService.insert(c, receiverId, String.format("₦%.2f received from User %d", amountKobo / 100.0, senderId) + note);

                c.commit();
                return senderBalAfter;
            } catch (RuntimeException e) {
                c.rollback();

                if (isUniqueViolation(e.getCause())) {
                    return -1;
                }
                throw e;
            } catch (SQLException e) {
                c.rollback();
                if ("23505".equals(e.getSQLState())) {
                    return -1;
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                c.rollback();
                if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    private static boolean isUniqueViolation(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException && "23505".equals(((SQLException) c).getSQLState())) return true;
        }
        return false;
    }
}