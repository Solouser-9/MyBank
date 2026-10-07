package bank;

public class Validate {


    public static String requireNonBlank(String val, String field) {
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return val.trim();
    }


    public static String requireEmail(String val) {
        val = requireNonBlank(val, "Email").toLowerCase();
        if (!val.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return val;
    }

    public static String requirePhone(String val) {
        val = requireNonBlank(val, "Phone");

        if (!val.matches("^\\+[0-9]{10,15}$")) {
            throw new IllegalArgumentException("Invalid phone format - use international format, e.g. +2348012345678");
        }
        return val;
    }


    public static long requireKobo(long val, long min, long max, String field) {
        if (val < min || val > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + " kobo");
        }
        return val;
    }


    public static String requireDigits(String val, int minLen, int maxLen, String field) {
        val = requireNonBlank(val, field);
        if (!val.matches("^\\d{" + minLen + "," + maxLen + "}$")) {
            throw new IllegalArgumentException(field + " must be between " + minLen + " and " + maxLen + " digits");
        }
        return val;
    }


    public static int requireInt(String val, int defaultVal, int min, int max) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        try {
            int parsed = Integer.parseInt(val.trim());
            if (parsed < min) return min;
            if (parsed > max) return max;
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value");
        }
    }


    public static String requireStrongPassword(String val, String field) {
        val = requireNonBlank(val, field);
        if (val.length() < 8) {
            throw new IllegalArgumentException(field + " must be at least 8 characters");
        }
        if (val.length() > 128) {
            throw new IllegalArgumentException(field + " must be at most 128 characters");
        }
        if (!val.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException(field + " must contain at least one uppercase letter");
        }
        if (!val.matches(".*[a-z].*")) {
            throw new IllegalArgumentException(field + " must contain at least one lowercase letter");
        }
        if (!val.matches(".*[0-9].*")) {
            throw new IllegalArgumentException(field + " must contain at least one number");
        }
        return val;
    }
}