package bank;

public class TestBillPaymentAdapter implements BillPaymentAdapter {

    @Override
    public boolean pay(String biller, String customerRef, long amountKobo, String reference) {






        if (reference != null && reference.toLowerCase().contains("fail")) {
            System.out.println("[TestBillPaymentAdapter] simulated FAILURE for reference=" + reference);
            return false;
        }
        System.out.println("[TestBillPaymentAdapter] accepted " + biller + ":" + customerRef
                + " amountKobo=" + amountKobo + " ref=" + reference);
        return true;
    }
}