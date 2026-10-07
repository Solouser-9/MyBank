package bank;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public class EmailOtpDeliveryService implements OtpDeliveryService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();


    public void deliver(long userId, String destination, String code, String purpose) {
        try {

            String body = Json.write(Map.of(
                    "sender", Map.of("name", "MyBank", "email", Config.get("BREVO_SENDER_EMAIL")),
                    "to", java.util.List.of(Map.of("email", destination)),
                    "subject", "MyBank verification code: " + code,
                    "htmlContent", "<p>Your MyBank verification code is:</p>"
                            + "<h2 style='font-size:32px;letter-spacing:8px;'>" + code + "</h2>"
                            + "<p>Expires in 10 minutes. If you didn't request this code, "
                            + "please ignore this email.</p>"
            ));

            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", Config.get("BREVO_API_KEY"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());




            if (resp.statusCode() >= 300) {
                throw new RuntimeException("Brevo send failed: HTTP " + resp.statusCode()
                        + " - " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }
}