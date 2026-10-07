package bank;

import java.util.List;
import java.util.Map;

public interface Provider {

    void createCustomerAndVa(long userId);


    default List<Map<String, String>> listBanks() {
        return List.of();
    }


    default String resolveAccountName(String accountNumber, String bankCode) {
        return null;
    }


    default String verifyTransfer(String reference) {
        return "pending";
    }




    default void createTransfer(String name, String acct, String bankCode, long kobo, String reference, String narration) {
    }
}