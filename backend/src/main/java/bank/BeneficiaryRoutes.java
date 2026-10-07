package bank;

import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BeneficiaryRoutes {


    public static void list(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");


            List<Map<String, Object>> list = new ArrayList<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT id, name, bank_code, account_number, created_at FROM beneficiaries WHERE user_id = ? ORDER BY created_at DESC")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(Map.of(
                                "id", rs.getLong("id"),
                                "name", rs.getString("name"),
                                "bankCode", rs.getString("bank_code"),
                                "accountNumber", rs.getString("account_number"),
                                "createdAt", rs.getTimestamp("created_at").toInstant().toString()
                        ));
                    }
                }
            }


            Json.send(ex, 200, list);
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void add(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");
            String reqId = (String) ex.getAttribute("requestId");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);


            if (!body.containsKey("name") || !body.containsKey("bankCode") ||
                    !body.containsKey("accountNumber") || !body.containsKey("otpCode")) {
                Json.send(ex, 400, Map.of("error", "Missing required fields"));
                return;
            }

            String name = String.valueOf(body.get("name"));
            String bankCode = String.valueOf(body.get("bankCode"));
            String accountNumber = String.valueOf(body.get("accountNumber"));
            String otpCode = String.valueOf(body.get("otpCode"));


            if (name == null || name.trim().isEmpty() || "null".equals(name)) throw new IllegalArgumentException("Name cannot be blank");
            if (bankCode == null || bankCode.trim().isEmpty() || "null".equals(bankCode)) throw new IllegalArgumentException("Bank code cannot be blank");
            if (accountNumber == null || accountNumber.trim().isEmpty() || "null".equals(accountNumber)) throw new IllegalArgumentException("Account number cannot be blank");


            OtpService.verify(userId, "beneficiary", otpCode);

            Db.update("INSERT INTO beneficiaries (user_id, name, bank_code, account_number) VALUES (?, ?, ?, ?)",
                    userId, name, bankCode, accountNumber);


            Audit.ev(userId, "beneficiary.added", accountNumber, Map.of("bank", bankCode), "success", null, reqId);

            Json.send(ex, 200, Map.of("status", "Beneficiary added"));
        } catch (IllegalArgumentException e) {
            try { Json.send(ex, 400, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void resolveInternal(HttpExchange ex) {
        try {

            String acct = null;
            String query = ex.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && "account_number".equals(pair[0])) acct = pair[1];
                }
            }

            if (acct == null || acct.isEmpty()) {
                Json.send(ex, 400, Map.of("error", "account_number required"));
                return;
            }


            Map<String, Object> va = Db.one(
                    "SELECT v.user_id, u.first_name, u.last_name FROM virtual_accounts v "
                            + "JOIN users u ON u.id = v.user_id WHERE v.account_number = ?", acct);
            if (va == null) {
                Json.send(ex, 404, Map.of("error", "Not a MyBank account"));
                return;
            }


            long receiverId = (long) va.get("user_id");
            String fullName = va.get("first_name") + " " + va.get("last_name");

            Json.send(ex, 200, Map.of("receiverId", receiverId, "accountName", fullName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}