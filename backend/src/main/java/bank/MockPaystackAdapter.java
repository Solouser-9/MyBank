package bank;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

public class MockPaystackAdapter implements Provider {


    @Override
    public void createCustomerAndVa(long userId) {
        try (Connection c = Db.conn()) {

            if (Db.exists(c, "SELECT 1 FROM virtual_accounts WHERE user_id = ?", userId)) {
                return;
            }
            String acct = "99" + String.format("%08d", userId);
            Db.update("INSERT INTO virtual_accounts (user_id, provider, account_number, bank_name, provider_ref, status) VALUES (?, 'mock', ?, 'Mock Bank', ?, 'active')",
                    userId, acct, "mock-ref-" + userId);
        } catch (Exception e) {
            Audit.ev(userId, "va.failed", "mock", Map.of("error", e.getMessage()), "failure", null, null);
            throw new RuntimeException(e);
        }
    }


    @Override
    public List<Map<String, String>> listBanks() {
        return List.of(
                Map.of("code", "033", "name", "United Bank for Africa"),
                Map.of("code", "044", "name", "Access Bank"),
                Map.of("code", "058", "name", "Guaranty Trust Bank"),
                Map.of("code", "999", "name", "Mock Bank")
        );
    }


    @Override
    public String resolveAccountName(String accountNumber, String bankCode) {
        if (accountNumber == null || accountNumber.length() != 10) {
            return null;
        }
        return "Mock User " + accountNumber;
    }



    @Override
    public void createTransfer(String name, String acct, String bankCode, long kobo, String reference, String narration) {

        System.out.println("[MOCK PROVIDER] Transfer initiated: " + kobo + " kobo to " + name
                + " (" + acct + ") ref=" + reference
                + (narration == null || narration.isEmpty() ? "" : " - " + narration));
    }

    @Override
    public String verifyTransfer(String reference) {

        return "successful";
    }
}