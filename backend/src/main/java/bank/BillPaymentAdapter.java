package bank;

public interface BillPaymentAdapter {











    boolean pay(String biller, String customerRef, long amountKobo, String reference);
}