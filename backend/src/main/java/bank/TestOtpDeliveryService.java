package bank;

public class TestOtpDeliveryService implements OtpDeliveryService {

    public void deliver(long userId, String destination, String code, String purpose) {
        System.out.println("[TEST OTP] user=" + userId + " purpose=" + purpose + " code=" + code);
    }
}