package bank;

import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

public class BankRoutes {


    private static final Provider provider = ProviderFactory.get();


    public static void listBanks(HttpExchange ex) {
        try {
            Json.send(ex, 200, provider.listBanks());
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void resolve(HttpExchange ex) {
        try {
            String query = ex.getRequestURI().getQuery();
            String acct = null;
            String bank = null;


            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2) {
                        if ("account_number".equals(pair[0])) acct = pair[1];
                        if ("bank_code".equals(pair[0])) bank = pair[1];
                    }
                }
            }


            if (acct == null || bank == null) {
                Json.send(ex, 400, Map.of("error", "account_number and bank_code required"));
                return;
            }


            String name = provider.resolveAccountName(acct, bank);
            if (name == null) {
                Json.send(ex, 400, Map.of("error", "Could not verify account"));
                return;
            }

            Json.send(ex, 200, Map.of("accountName", name));
        } catch (SecurityException e) {
            try { Json.send(ex, 401, Map.of("error", e.getMessage())); } catch (Exception ex2) { throw new RuntimeException(ex2); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}