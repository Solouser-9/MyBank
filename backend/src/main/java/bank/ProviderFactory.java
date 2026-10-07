package bank;

public class ProviderFactory {

    public static Provider get() {
        return "real".equals(Config.getOptional("PROVIDER"))
                ? new RealPaystackAdapter()
                : new MockPaystackAdapter();
    }
}