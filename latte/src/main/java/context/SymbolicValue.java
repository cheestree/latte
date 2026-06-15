package context;

public class SymbolicValue {

    int value;

    public SymbolicValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SymbolicValue && ((SymbolicValue) obj).value == value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "𝜈" + value ;
    }
}
