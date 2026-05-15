package rj_language.ast;

public final class LiteralInt extends Expression {
    private final long value;

    public LiteralInt(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitLiteralInt(this);
    }
}
