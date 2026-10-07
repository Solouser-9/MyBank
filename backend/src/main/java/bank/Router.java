package bank;

import com.sun.net.httpserver.HttpExchange;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Router {

    public static final Map<String, Consumer<HttpExchange>> routes = new HashMap<>();


    public static void registerAll() {

        routes.put("GET /api/health", ex -> {
            try {
                Json.send(ex, 200, Map.of("status", "ok", "time", java.time.Instant.now().toString()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });


        routes.put("POST /api/auth/register", AuthRoutes::register);
        routes.put("POST /api/auth/verify-otp", AuthRoutes::verifyOtp);
        routes.put("POST /api/auth/login", AuthRoutes::login);
        routes.put("POST /api/auth/verify-login-otp", AuthRoutes::verifyLoginOtp);
        routes.put("POST /api/auth/refresh", AuthRoutes::refresh);
        routes.put("POST /api/auth/logout", AuthRoutes::logout);
        routes.put("POST /api/auth/set-pin", AuthRoutes::setPin);
        routes.put("POST /api/auth/change-password", AuthRoutes::changePassword);


        routes.put("POST /api/kyc/submit", KycRoutes::submit);
        routes.put("POST /webhooks/paystack", WebhookRoutes::paystack);


        routes.put("POST /api/otp/request", TransferRoutes::requestOtp);
        routes.put("POST /api/transfers/internal", TransferRoutes::internalTransfer);


        routes.put("GET /api/banks", BankRoutes::listBanks);
        routes.put("GET /api/banks/resolve", BankRoutes::resolve);
        routes.put("GET /api/beneficiaries", BeneficiaryRoutes::list);
        routes.put("GET /api/beneficiaries/resolve", BeneficiaryRoutes::resolveInternal);
        routes.put("POST /api/beneficiaries", BeneficiaryRoutes::add);


        routes.put("POST /api/transfers/external", TransferRoutes::externalTransfer);
        routes.put("POST /api/bills/pay", BillRoutes::pay);


        routes.put("GET /api/me/dashboard", QueryRoutes::dashboard);
        routes.put("GET /api/me/analytics", QueryRoutes::analytics);
        routes.put("GET /api/transactions", QueryRoutes::transactions);
        routes.put("GET /api/statements", QueryRoutes::statements);
        routes.put("POST /api/statements/verify", QueryRoutes::verifyStatement);
        routes.put("GET /api/notifications", NotificationRoutes::list);
        routes.put("POST /api/notifications/read", NotificationRoutes::markAllRead);
        routes.put("POST /api/notifications/read-one", NotificationRoutes::readOne);
    }
}