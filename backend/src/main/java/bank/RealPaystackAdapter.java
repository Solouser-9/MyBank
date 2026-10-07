package bank;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;








public class RealPaystackAdapter implements Provider {


    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();


    private static final String BASE = "https://api.paystack.co";





    @SuppressWarnings("unchecked")
    @Override
    public void createCustomerAndVa(long userId) {
        String requestId = java.util.UUID.randomUUID().toString();
        try {

            Map<String, Object> existingVa = Db.one("SELECT id FROM virtual_accounts WHERE user_id = ?", userId);
            if (existingVa != null) return;


            Map<String, Object> user = Db.one("SELECT email, first_name, last_name, phone FROM users WHERE id = ?", userId);
            if (user == null) return;

            String email = (String) user.get("email");
            String firstName = (String) user.get("first_name");
            String lastName = (String) user.get("last_name");
            String phone = (String) user.get("phone");


            Map<String, Object> custMap = new HashMap<>();
            custMap.put("email", email);
            custMap.put("first_name", firstName);
            custMap.put("last_name", lastName);
            if (phone != null) custMap.put("phone", phone);

            HttpRequest custReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/customer"))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(custMap)))
                    .build();
            HttpResponse<String> custRes = client.send(custReq, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> custBody = Json.parse(custRes.body(), Map.class);
            if (!Boolean.TRUE.equals(custBody.get("status"))) {
                throw new RuntimeException("Customer creation failed (HTTP " + custRes.statusCode() + ")");
            }
            Map<String, Object> custData = (Map<String, Object>) custBody.get("data");
            String customerCode = (String) custData.get("customer_code");
            System.out.println("[Paystack] customer created for user=" + userId);



            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/dedicated_account?customer=" + customerCode))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> checkRes = client.send(checkReq, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> checkBody = Json.parse(checkRes.body(), Map.class);

            String acctNum = null, bankName = null, providerRef = null;
            if (Boolean.TRUE.equals(checkBody.get("status")) && checkBody.get("data") instanceof List) {

                List<Map<String, Object>> dataList = (List<Map<String, Object>>) checkBody.get("data");
                for (Map<String, Object> acc : dataList) {
                    if (Boolean.TRUE.equals(acc.get("active"))) {
                        acctNum = (String) acc.get("account_number");
                        bankName = (String) ((Map<String, Object>) acc.get("bank")).get("name");
                        providerRef = String.valueOf(acc.get("id"));
                        break;
                    }
                }
            }


            if (acctNum == null) {

                HttpRequest vaReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/dedicated_account"))
                        .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of(
                                "customer", customerCode,
                                "preferred_bank", "test-bank"
                        ))))
                        .build();
                HttpResponse<String> vaRes = client.send(vaReq, HttpResponse.BodyHandlers.ofString());

                Map<String, Object> vaBody = Json.parse(vaRes.body(), Map.class);
                if (!Boolean.TRUE.equals(vaBody.get("status"))) {
                    throw new RuntimeException("VA creation failed (HTTP " + vaRes.statusCode() + ")");
                }
                Map<String, Object> vaData = (Map<String, Object>) vaBody.get("data");
                acctNum = (String) vaData.get("account_number");
                bankName = (String) ((Map<String, Object>) vaData.get("bank")).get("name");
                providerRef = String.valueOf(vaData.get("id"));
            }


            Db.update("INSERT INTO virtual_accounts (user_id, provider, account_number, bank_name, provider_ref) "
                            + "VALUES (?, 'paystack', ?, ?, ?) ON CONFLICT DO NOTHING",
                    userId, acctNum, bankName, providerRef);

            Audit.ev(userId, "va.issued", "virtual_accounts",
                    Map.of("account_number", acctNum, "bank", bankName), "success", null, requestId);

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            Audit.ev(userId, "va.failed", "virtual_accounts", Map.of("error", errorMsg), "failure", null, requestId);
            System.err.println("[Paystack] VA creation failed for user=" + userId + ": " + errorMsg);
        }
    }





    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, String>> listBanks() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/bank?currency=NGN"))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = Json.parse(res.body(), Map.class);
            if (!Boolean.TRUE.equals(body.get("status"))) {

                throw new RuntimeException("listBanks rejected (HTTP " + res.statusCode() + ")");
            }
            List<Map<String, Object>> banks = (List<Map<String, Object>>) body.get("data");
            List<Map<String, String>> out = new ArrayList<>();
            for (Map<String, Object> b : banks) {
                out.add(Map.of(
                        "code", String.valueOf(b.get("code")),
                        "name", String.valueOf(b.get("name"))));
            }
            return out;
        } catch (Exception e) {
            System.err.println("[Paystack] listBanks failed: " + e.getMessage());
            return List.of();
        }
    }






    @SuppressWarnings("unchecked")
    @Override
    public String resolveAccountName(String accountNumber, String bankCode) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/bank/resolve?account_number=" + accountNumber + "&bank_code=" + bankCode))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = Json.parse(res.body(), Map.class);

            if (!Boolean.TRUE.equals(body.get("status"))) return null;
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return (String) data.get("account_name");
        } catch (Exception e) {
            System.err.println("[Paystack] resolve failed: " + e.getMessage());
            return null;
        }
    }







    @SuppressWarnings("unchecked")


    @Override
    public void createTransfer(String name, String accountNumber, String bankCode, long kobo, String reference, String narration) {
        try {

            Map<String, Object> recPayload = new HashMap<>();
            recPayload.put("type", "nuban");
            recPayload.put("name", name);
            recPayload.put("account_number", accountNumber);
            recPayload.put("bank_code", bankCode);
            recPayload.put("currency", "NGN");

            HttpRequest recReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/transferrecipient"))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(recPayload)))
                    .build();
            HttpResponse<String> recRes = client.send(recReq, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> recBody = Json.parse(recRes.body(), Map.class);
            if (!Boolean.TRUE.equals(recBody.get("status"))) {
                throw new RuntimeException("recipient registration failed (HTTP " + recRes.statusCode() + ")");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> recData = (Map<String, Object>) recBody.get("data");
            String recipientCode = (String) recData.get("recipient_code");



            Map<String, Object> txPayload = new HashMap<>();
            txPayload.put("source", "balance");
            txPayload.put("amount", kobo);
            txPayload.put("recipient", recipientCode);
            txPayload.put("reference", reference);
            if (narration != null && !narration.isEmpty()) {
                txPayload.put("reason", narration);
            }

            HttpRequest txReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/transfer"))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(txPayload)))
                    .build();
            HttpResponse<String> txRes = client.send(txReq, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> txBody = Json.parse(txRes.body(), Map.class);

            if (!Boolean.TRUE.equals(txBody.get("status"))) {
                throw new RuntimeException("transfer initiation failed (HTTP " + txRes.statusCode() + ")");
            }
            System.out.println("[Paystack] transfer initiated ref=" + reference);


        } catch (Exception e) {
            throw new RuntimeException("Paystack transfer failed: " + e.getMessage(), e);
        }
    }







    @SuppressWarnings("unchecked")
    @Override
    public String verifyTransfer(String reference) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/transfer/verify/" + reference))
                    .header("Authorization", "Bearer " + Config.get("PAYSTACK_SECRET_KEY"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = Json.parse(res.body(), Map.class);

            if (!Boolean.TRUE.equals(body.get("status"))) return "pending";
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String s = String.valueOf(data.get("status"));
            if ("success".equalsIgnoreCase(s)) return "successful";
            return s;
        } catch (Exception e) {
            System.err.println("[Paystack] verify failed: " + e.getMessage());
            return "pending";
        }
    }
}