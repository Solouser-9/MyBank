package bank;

public interface OtpDeliveryService {

    void deliver(long userId, String destination, String code, String purpose);
}