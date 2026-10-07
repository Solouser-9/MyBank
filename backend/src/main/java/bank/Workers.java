package bank;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Workers {

    private static final ExecutorService pool = Executors.newFixedThreadPool(4);


    public static void submit(Runnable task) {
        pool.submit(task);
    }
}