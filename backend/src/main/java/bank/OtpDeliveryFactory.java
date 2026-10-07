package bank;

public class OtpDeliveryFactory {

    public static OtpDeliveryService get() {
        String channel = Config.get("OTP_CHANNEL");
        if ("email".equals(channel)) return new EmailOtpDeliveryService();
        return new TestOtpDeliveryService();
    }
}