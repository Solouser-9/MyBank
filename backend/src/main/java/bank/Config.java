package bank;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    private static final Map<String, String> env = new HashMap<>();


    public static void load() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(".env"));

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx > 0) {
                    env.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
        } catch (Exception e) {
            System.out.println("No .env file found, relying on system environment variables.");
        }
    }


    public static String get(String key) {
        String val = env.getOrDefault(key, System.getenv(key));
        if (val == null || val.trim().isEmpty()) {
            throw new RuntimeException("Missing mandatory environment variable: " + key);
        }
        return val;
    }




    public static String getOptional(String key) {
        String val = env.getOrDefault(key, System.getenv(key));
        return (val == null || val.trim().isEmpty()) ? null : val;
    }


    public static long longOf(String key) {
        return Long.parseLong(get(key));
    }
}