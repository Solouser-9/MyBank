package bank;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class Json {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    public static String write(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON write error", e);
        }
    }


    public static <T> T parse(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON");
        }
    }


    public static void send(HttpExchange ex, int status, Object obj) {
        try {
            String response = write(obj);
            byte[] bytes = response.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static byte[] raw(HttpExchange ex) {
        try (InputStream is = ex.getRequestBody(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int nRead;
            int total = 0;

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                total += nRead;
                if (total > 1_000_001) {
                    throw new RuntimeException("Request too large");
                }
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error reading request body", e);
        }
    }


    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(HttpExchange ex) {
        byte[] rawBytes = raw(ex);
        if (rawBytes.length == 0) return Map.of();
        try {
            return mapper.readValue(rawBytes, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON");
        }
    }
}