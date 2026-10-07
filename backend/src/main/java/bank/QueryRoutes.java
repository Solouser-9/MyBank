package bank;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryRoutes {


    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {

            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    try {
                        map.put(pair[0], java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }


    private static String escapeCsv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-") || escaped.startsWith("@")) {
            escaped = "'" + escaped;
        }

        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static boolean isValidDate(String s) {
        // LAYER 3: Input validation - reject malformed and impossible calendar dates before querying.
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            java.time.LocalDate.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public static void dashboard(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");

            Map<String, Object> dash = new HashMap<>();
            try (Connection c = Db.conn()) {

                try (PreparedStatement ps = c.prepareStatement("SELECT first_name, kyc_status, pin_hash FROM users WHERE id = ?")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dash.put("user", Map.of(
                                    "first_name", rs.getString("first_name"),
                                    "kyc_status", rs.getString("kyc_status"),
                                    "hasPin", rs.getString("pin_hash") != null));
                        }
                    }
                }


                dash.put("balanceKobo", Db.scalar(c, "SELECT balance_kobo FROM wallets WHERE user_id = ?", userId));

                try (PreparedStatement ps = c.prepareStatement("SELECT account_number, bank_name FROM virtual_accounts WHERE user_id = ? AND status = 'active'")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dash.put("virtualAccount", Map.of("account_number", rs.getString("account_number"), "bank_name", rs.getString("bank_name")));
                        } else {
                            dash.put("virtualAccount", null);
                        }
                    }
                }


                List<Map<String, Object>> recent = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM wallet_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT 5")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            recent.add(Map.of(
                                    "reference", rs.getString("reference"),
                                    "type", rs.getString("type"),
                                    "amount_kobo", rs.getLong("amount_kobo"),
                                    "status", rs.getString("status"),
                                    "counterparty", rs.getString("counterparty") != null ? rs.getString("counterparty") : "",
                                    "created_at", rs.getTimestamp("created_at").toInstant().toString()
                            ));
                        }
                    }
                }
                dash.put("recentTransactions", recent);


                dash.put("monthInKobo", Db.scalar(c, "SELECT COALESCE(SUM(amount_kobo), 0) FROM wallet_transactions WHERE user_id = ? AND type = 'credit' AND status = 'successful' AND created_at >= date_trunc('month', current_date)", userId));
                dash.put("monthOutKobo", Db.scalar(c, "SELECT COALESCE(SUM(amount_kobo), 0) FROM wallet_transactions WHERE user_id = ? AND type = 'debit' AND status = 'successful' AND created_at >= date_trunc('month', current_date)", userId));
                dash.put("unreadNotifications", Db.scalar(c, "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read = FALSE", userId));
            }

            Json.send(ex, 200, dash);
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void transactions(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");
            Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());


            if (q.containsKey("from") && !isValidDate(q.get("from"))) {
                Json.send(ex, 400, Map.of("error", "Invalid 'from' date - use YYYY-MM-DD"));
                return;
            }
            if (q.containsKey("to") && !isValidDate(q.get("to"))) {
                Json.send(ex, 400, Map.of("error", "Invalid 'to' date - use YYYY-MM-DD"));
                return;
            }


            int limit = 20;
            int offset = 0;
            try { if (q.containsKey("limit")) limit = Integer.parseInt(q.get("limit")); } catch (Exception ignored) {}
            try { if (q.containsKey("offset")) offset = Integer.parseInt(q.get("offset")); } catch (Exception ignored) {}

            if (limit < 1) limit = 1;
            if (limit > 100) limit = 100;
            if (offset < 0) offset = 0;


            List<Object> params = new ArrayList<>();
            params.add(userId);
            StringBuilder sql = new StringBuilder("SELECT * FROM wallet_transactions WHERE user_id = ?");

            if (q.containsKey("status")) { sql.append(" AND status = ?"); params.add(q.get("status")); }
            if (q.containsKey("type")) { sql.append(" AND type = ?"); params.add(q.get("type")); }
            if (q.containsKey("from")) { sql.append(" AND created_at >= ?::date"); params.add(q.get("from")); }
            if (q.containsKey("to")) { sql.append(" AND created_at < (?::date + interval '1 day')"); params.add(q.get("to")); }
            if (q.containsKey("search")) { sql.append(" AND counterparty ILIKE ?"); params.add("%" + q.get("search") + "%"); }

            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);


            List<Map<String, Object>> list = new ArrayList<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(Map.of(
                                "reference", rs.getString("reference"),
                                "type", rs.getString("type"),
                                "amount_kobo", rs.getLong("amount_kobo"),
                                "fee_kobo", rs.getLong("fee_kobo"),
                                "balance_after_kobo", rs.getLong("balance_after_kobo"),
                                "status", rs.getString("status"),
                                "counterparty", rs.getString("counterparty") != null ? rs.getString("counterparty") : "",
                                "created_at", rs.getTimestamp("created_at").toInstant().toString()
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


    public static void statements(HttpExchange ex) {
        try {

            long userId = (Long) ex.getAttribute("userId");
            Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());

            String from = q.get("from");
            String to = q.get("to");
            if (from == null || to == null) {
                Json.send(ex, 400, Map.of("error", "from and to dates are required"));
                return;
            }

            if (!isValidDate(from) || !isValidDate(to)) {
                Json.send(ex, 400, Map.of("error", "Invalid date - use YYYY-MM-DD"));
                return;
            }


            String acct = "N/A";
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT account_number FROM virtual_accounts WHERE user_id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) acct = rs.getString(1);
                }
            }
            String maskedAcct = acct.length() >= 10 ? acct.substring(0, 4) + "****" + acct.substring(acct.length() - 2) : acct;


            String holderName = "MyBank Customer";
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT first_name, last_name FROM users WHERE id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        holderName = rs.getString("first_name") + " " + rs.getString("last_name");
                    }
                }
            }


            long openingBal = 0;
            boolean found = false;

            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT type, amount_kobo, fee_kobo, balance_after_kobo FROM wallet_transactions WHERE user_id = ? AND created_at >= ?::date AND created_at < (?::date + interval '1 day') ORDER BY created_at ASC LIMIT 1")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        found = true;
                        String type = rs.getString("type");
                        long amt = rs.getLong("amount_kobo");
                        long fee = rs.getLong("fee_kobo");
                        long balAfter = rs.getLong("balance_after_kobo");
                        // LAYER 3: Opening balance - reverse the first in-range transaction from its ending balance.
                        if ("debit".equals(type)) {
                            openingBal = balAfter + amt + fee;
                        } else {
                            openingBal = balAfter - amt;
                        }
                    }
                }
            }


            if (!found) {
                try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT balance_after_kobo FROM wallet_transactions WHERE user_id = ? AND created_at < ?::date ORDER BY created_at DESC LIMIT 1")) {
                    ps.setLong(1, userId);
                    ps.setString(2, from);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            found = true;
                            openingBal = rs.getLong(1);
                        }
                    }
                }
            }


            if (!found) {
                try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT balance_kobo FROM wallets WHERE user_id = ?")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) openingBal = rs.getLong(1);
                    }
                }
            }


            StringBuilder csv = new StringBuilder();
            csv.append("Account Number,").append(escapeCsv(maskedAcct)).append("\n");
            csv.append("Account Holder,").append(escapeCsv(holderName)).append("\n");
            csv.append("Period,").append(escapeCsv(from + " to " + to)).append("\n");
            csv.append("Opening Balance,").append(openingBal).append("\n\n");
            csv.append("Date,Reference,Type,Amount,Fee,Counterparty,Status,Balance\n");


            long closingBal = openingBal;
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM wallet_transactions WHERE user_id = ? AND created_at >= ?::date AND created_at < (?::date + interval '1 day') ORDER BY created_at ASC")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        csv.append(escapeCsv(rs.getTimestamp("created_at").toInstant().toString())).append(",");
                        csv.append(escapeCsv(rs.getString("reference"))).append(",");
                        csv.append(escapeCsv(rs.getString("type"))).append(",");
                        csv.append(rs.getLong("amount_kobo")).append(",");
                        csv.append(rs.getLong("fee_kobo")).append(",");
                        csv.append(escapeCsv(rs.getString("counterparty"))).append(",");
                        csv.append(escapeCsv(rs.getString("status"))).append(",");
                        closingBal = rs.getLong("balance_after_kobo");
                        csv.append(closingBal).append("\n");
                    }
                }
            }
            csv.append("\nClosing Balance,").append(closingBal).append("\n");

            // LAYER 3: Statement security - sign the exact CSV bytes and append the signature and public key.
            byte[] signedBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            String signatureB64 = StatementSigner.sign(userId, signedBytes);
            String publicKeyB64 = StatementSigner.publicKeyB64(userId);
            csv.append("Signature,").append(signatureB64).append("\n");
            csv.append("Public Key,").append(publicKeyB64).append("\n");


            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"statement.csv\"");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();

        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void verifyStatement(HttpExchange ex) {
        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Json.body(ex);
            if (!body.containsKey("statement") || String.valueOf(body.get("statement")).isBlank()) {
                Json.send(ex, 400, Map.of("error", "statement text is required"));
                return;
            }
            String text = String.valueOf(body.get("statement"));


            int sigIdx = text.lastIndexOf("\nSignature,");
            if (sigIdx < 0) {
                Json.send(ex, 200, Map.of("valid", false,
                        "reason", "No signature found - the statement is unsigned or the seal lines were removed"));
                return;
            }
            int pkIdx = text.lastIndexOf("\nPublic Key,");
            if (pkIdx < 0 || pkIdx < sigIdx) {
                Json.send(ex, 200, Map.of("valid", false,
                        "reason", "Public Key line missing - the seal is incomplete"));
                return;
            }

            String sigB64 = text.substring(sigIdx + 1 + "Signature,".length()).split("\n", 2)[0].trim();
            String pubB64 = text.substring(pkIdx + 1 + "Public Key,".length()).split("\n", 2)[0].trim();
            String signedText = text.substring(0, sigIdx + 1);


            Map<String, Object> owner = Db.one(
                    "SELECT id, first_name, last_name FROM users WHERE signing_public_key = ?", pubB64);
            if (owner == null) {
                Json.send(ex, 200, Map.of("valid", false,
                        "reason", "Unknown signing key - this statement was not issued by this bank"));
                return;
            }
            long ownerId = (long) owner.get("id");

            String storedPub = StatementSigner.publicKeyB64(ownerId);
            if (!Crypto.secureCompare(pubB64, storedPub)) {
                Json.send(ex, 200, Map.of("valid", false,
                        "reason", "Embedded public key does not match bank records"));
                return;
            }


            byte[] signedBytes = signedText.getBytes(StandardCharsets.UTF_8);
            byte[] sigBytes;
            try {
                sigBytes = java.util.Base64.getDecoder().decode(sigB64);
            } catch (IllegalArgumentException e) {
                Json.send(ex, 200, Map.of("valid", false,
                        "reason", "Signature is not valid Base64 - the seal line was altered"));
                return;
            }

            boolean ok = StatementSigner.verify(ownerId, signedBytes, sigBytes);

            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("valid", ok);
            resp.put("accountHolder", owner.get("first_name") + " " + owner.get("last_name"));
            resp.put("reason", ok
                    ? "Signature intact - statement is unmodified and issued to this account"
                    : "Signature INVALID - the statement has been modified or was not issued to this account");
            Json.send(ex, 200, resp);

        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void analytics(HttpExchange ex) {
        try {
            long userId = (Long) ex.getAttribute("userId");
            Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());


            String from;
            String to;
            if (q.containsKey("from") || q.containsKey("to")) {
                from = q.get("from");
                to = q.get("to");
                if (from == null || to == null) {
                    Json.send(ex, 400, Map.of("error", "from and to are required together - use YYYY-MM-DD"));
                    return;
                }
                if (!isValidDate(from) || !isValidDate(to)) {
                    Json.send(ex, 400, Map.of("error", "Invalid date - use YYYY-MM-DD"));
                    return;
                }
                if (from.compareTo(to) > 0) {
                    Json.send(ex, 400, Map.of("error", "'from' must be on or before 'to'"));
                    return;
                }
            } else {
                int months = Validate.requireInt(q.get("months"), 6, 1, 24);
                java.time.LocalDate first = java.time.LocalDate.now().minusMonths(months - 1L).withDayOfMonth(1);
                from = first.toString();
                to = java.time.LocalDate.now().toString();
            }


            long spanMonths = java.time.temporal.ChronoUnit.MONTHS.between(
                    java.time.YearMonth.parse(from.substring(0, 7)),
                    java.time.YearMonth.parse(to.substring(0, 7))) + 1;
            if (spanMonths > 24) {
                Json.send(ex, 400, Map.of("error", "Range too long - pick at most 24 months"));
                return;
            }


            long opening = 0;
            boolean found = false;
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT balance_after_kobo FROM wallet_transactions WHERE user_id = ? AND created_at < ?::date ORDER BY created_at DESC LIMIT 1")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        opening = rs.getLong(1);
                        found = true;
                    }
                }
            }

            if (!found) {
                try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                        "SELECT type, amount_kobo, fee_kobo, balance_after_kobo FROM wallet_transactions "
                                + "WHERE user_id = ? AND created_at >= ?::date AND created_at < (?::date + interval '1 day') "
                                + "ORDER BY created_at ASC LIMIT 1")) {
                    ps.setLong(1, userId);
                    ps.setString(2, from);
                    ps.setString(3, to);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            found = true;
                            long balAfter = rs.getLong("balance_after_kobo");
                            if ("debit".equals(rs.getString("type"))) {
                                opening = balAfter + rs.getLong("amount_kobo") + rs.getLong("fee_kobo");
                            } else {
                                opening = balAfter - rs.getLong("amount_kobo");
                            }
                        }
                    }
                }
            }

            if (!found) {
                try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                        "SELECT balance_kobo FROM wallets WHERE user_id = ?")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) opening = rs.getLong(1);
                    }
                }
            }


            Map<String, long[]> flows = new java.util.LinkedHashMap<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS ym, "
                            + "COALESCE(SUM(CASE WHEN type = 'credit' THEN amount_kobo ELSE 0 END), 0) AS inflow, "
                            + "COALESCE(SUM(CASE WHEN type = 'debit' THEN amount_kobo ELSE 0 END), 0) AS outflow, "
                            + "COALESCE(SUM(fee_kobo), 0) AS fees "
                            + "FROM wallet_transactions WHERE user_id = ? AND status = 'successful' "
                            + "AND created_at >= ?::date AND created_at < (?::date + interval '1 day') "
                            + "GROUP BY 1 ORDER BY 1")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        flows.put(rs.getString("ym"),
                                new long[]{ rs.getLong("inflow"), rs.getLong("outflow"), rs.getLong("fees") });
                    }
                }
            }


            Map<String, Long> endByMonth = new HashMap<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT DISTINCT ON (date_trunc('month', created_at)) "
                            + "to_char(date_trunc('month', created_at), 'YYYY-MM') AS ym, balance_after_kobo "
                            + "FROM wallet_transactions WHERE user_id = ? "
                            + "AND created_at >= ?::date AND created_at < (?::date + interval '1 day') "
                            + "ORDER BY date_trunc('month', created_at), created_at DESC")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) endByMonth.put(rs.getString("ym"), rs.getLong("balance_after_kobo"));
                }
            }


            String firstActivityYm = null;
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT to_char(date_trunc('month', MIN(created_at)), 'YYYY-MM') FROM wallet_transactions WHERE user_id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) firstActivityYm = rs.getString(1);
                }
            }


            java.time.YearMonth cursor = java.time.YearMonth.parse(from.substring(0, 7));
            java.time.YearMonth lastM = java.time.YearMonth.parse(to.substring(0, 7));
            List<Map<String, Object>> monthsList = new ArrayList<>();
            long carry = opening;
            long tIn = 0, tOut = 0, tFees = 0;
            while (!cursor.isAfter(lastM)) {
                String ym = cursor.toString();
                long[] f = flows.getOrDefault(ym, new long[]{0, 0, 0});
                tIn += f[0];
                tOut += f[1];
                tFees += f[2];
                Long end = endByMonth.get(ym);
                if (end != null) carry = end;
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("ym", ym);
                row.put("inflowKobo", f[0]);
                row.put("outflowKobo", f[1]);
                row.put("endBalanceKobo", carry);
                monthsList.add(row);
                cursor = cursor.plusMonths(1);
            }


            long current;
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT balance_kobo FROM wallets WHERE user_id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    current = rs.next() ? rs.getLong(1) : 0;
                }
            }


            Map<String, Long> byCategory = new java.util.LinkedHashMap<>();
            List<Map<String, Object>> topRecipients = new ArrayList<>();
            try (Connection c = Db.conn(); PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(counterparty, '') AS cp, COALESCE(category, 'external') AS cat, "
                            + "SUM(amount_kobo) AS total "
                            + "FROM wallet_transactions WHERE user_id = ? AND type = 'debit' AND status = 'successful' "
                            + "AND created_at >= ?::date AND created_at < (?::date + interval '1 day') "
                            + "GROUP BY 1, 2 ORDER BY total DESC")) {
                ps.setLong(1, userId);
                ps.setString(2, from);
                ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long total = rs.getLong("total");
                        if (total <= 0) continue;
                        String cp = rs.getString("cp");
                        byCategory.merge(rs.getString("cat"), total, Long::sum);
                        if (topRecipients.size() < 5) {
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("name", cp.isEmpty() ? "Unknown" : cp);
                            row.put("totalKobo", total);
                            topRecipients.add(row);
                        }
                    }
                }
            }


            Map<String, String> CATEGORY_LABELS = Map.of(
                    "internal", "MyBank users",
                    "bills", "Bills",
                    "external", "Other banks",
                    "funding", "Funding");

            List<Map<String, Object>> categories = new ArrayList<>();
            for (Map.Entry<String, Long> e : byCategory.entrySet()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("category", CATEGORY_LABELS.getOrDefault(e.getKey(), e.getKey()));
                row.put("totalKobo", e.getValue());
                categories.add(row);
            }


            Map<String, Object> cur = Db.one(
                    "SELECT COALESCE(SUM(CASE WHEN type = 'credit' THEN amount_kobo ELSE 0 END), 0) AS inflow, "
                            + "COALESCE(SUM(CASE WHEN type = 'debit' THEN amount_kobo ELSE 0 END), 0) AS outflow "
                            + "FROM wallet_transactions WHERE user_id = ? AND status = 'successful' "
                            + "AND created_at >= date_trunc('month', current_date)", userId);
            Map<String, Object> avg = Db.one(
                    "SELECT COALESCE(AVG(inflow), 0) AS avg_in, COALESCE(AVG(outflow), 0) AS avg_out FROM ( "
                            + "SELECT date_trunc('month', created_at) AS mo, "
                            + "SUM(CASE WHEN type = 'credit' THEN amount_kobo ELSE 0 END) AS inflow, "
                            + "SUM(CASE WHEN type = 'debit' THEN amount_kobo ELSE 0 END) AS outflow "
                            + "FROM wallet_transactions WHERE user_id = ? AND status = 'successful' "
                            + "AND created_at >= date_trunc('month', current_date) - interval '3 months' "
                            + "AND created_at < date_trunc('month', current_date) "
                            + "GROUP BY 1) t", userId);
            long thisMonthIn = cur != null && cur.get("inflow") != null ? ((Number) cur.get("inflow")).longValue() : 0;
            long thisMonthOut = cur != null && cur.get("outflow") != null ? ((Number) cur.get("outflow")).longValue() : 0;
            long avgIn = avg != null && avg.get("avg_in") != null ? Math.round(((Number) avg.get("avg_in")).doubleValue()) : 0;
            long avgOut = avg != null && avg.get("avg_out") != null ? Math.round(((Number) avg.get("avg_out")).doubleValue()) : 0;


            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("from", from);
            resp.put("to", to);
            resp.put("openingBalanceKobo", opening);
            resp.put("currentBalanceKobo", current);
            resp.put("months", monthsList);
            resp.put("totals", Map.of("inKobo", tIn, "outKobo", tOut, "feesKobo", tFees));
            resp.put("categories", categories);
            resp.put("topRecipients", topRecipients);
            resp.put("comparison", Map.of(
                    "thisMonthInKobo", thisMonthIn,
                    "thisMonthOutKobo", thisMonthOut,
                    "avgInKobo", avgIn,
                    "avgOutKobo", avgOut));
            if (firstActivityYm != null) resp.put("firstActivityYm", firstActivityYm);
            Json.send(ex, 200, resp);

        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}