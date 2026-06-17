package utils;

public class Utils {
    /**
     * Throws an IllegalStateException with the given message if the value is null, otherwise returns the value.
     * @param value the value to check for null
     * @param msg the message to include in the exception if the value is null
     * @return the value if it is not null
     */
    public static <T> T getOrThrow(T value, String msg) {
        if (value == null) {
            throw new IllegalStateException(msg);
        }
        return value;
    }
}
