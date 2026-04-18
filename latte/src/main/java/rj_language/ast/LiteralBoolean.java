package rj_language.ast;

public final class LiteralBoolean extends Expression {
    private final boolean value;

    public LiteralBoolean(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitLiteralBoolean(this);
    }
}
