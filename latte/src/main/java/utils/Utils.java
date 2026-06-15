package utils;

public class Utils {
    public static <T> T getOrThrow(T value, IllegalStateException exception) {
        if (value == null) {
            throw exception;
        }
        return value;
    }
}
