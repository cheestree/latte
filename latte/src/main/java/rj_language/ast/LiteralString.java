package rj_language.ast;

public final class LiteralString extends Expression {
    private final String value;

    public LiteralString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitLiteralString(this);
    }
}
